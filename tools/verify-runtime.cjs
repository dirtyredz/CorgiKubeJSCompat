// Offline installed-server verification. Every output and copied input stays in this worktree.
const fs = require('node:fs');
const path = require('node:path');
const cp = require('node:child_process');
const os = require('node:os');
const crypto = require('node:crypto');
const root = path.resolve(__dirname, '..');
const [stageArg, javaArg, scenario = 'control'] = process.argv.slice(2);
if (!stageArg || !javaArg || !['control', 'writer-first', 'reader-first', 'baseline', 'plain-base', 'plain-fixed'].includes(scenario)) {
  throw Error('Usage: node tools/verify-runtime.cjs <saved-stage> <java17.exe> <scenario>');
}
const stage = path.resolve(stageArg), java = path.resolve(javaArg);
const instrumented = !scenario.startsWith('plain-');
const fixed = ['writer-first', 'reader-first', 'plain-fixed'].includes(scenario);
const run = path.join(root, 'run', `${scenario}-${Date.now()}`);
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));

function processes() {
  const command = "@(Get-CimInstance Win32_Process | Where-Object { $_.Name -match '^java(w)?\\.exe$' } | Select-Object ProcessId,CommandLine) | ConvertTo-Json -Compress";
  const output = cp.execFileSync('powershell.exe', ['-NoProfile', '-Command', command], {encoding:'utf8', windowsHide:true}).trim();
  const list = output ? JSON.parse(output) : [];
  return (Array.isArray(list) ? list : [list]).filter(p =>
    /GradleWrapperMain|GradleWorkerMain|BootstrapLauncher|win_args|probe\.args|net\.minecraftforge\.renamer/.test(p.CommandLine || ''));
}

async function main() {
  for (let minute = 0; ; minute++) {
    const busy = processes();
    if (!busy.length) break;
    if (minute >= 45) throw Error('Machine remained busy for 45 minutes: ' + JSON.stringify(busy));
    console.log('Waiting for existing build/server:', busy.map(p => p.ProcessId).join(','));
    await sleep(60000);
  }
  fs.mkdirSync(run, {recursive:true});
  // Copy, never link: servers can rewrite configs and libraries. No launchers or installers.
  for (const name of ['libraries', 'mods', 'config', 'defaultconfigs', 'kubejs']) {
    fs.cpSync(path.join(stage, name), path.join(run, name), {recursive:true});
  }
  const properties = fs.readFileSync(path.join(stage, 'server.properties'), 'utf8')
    .replace(/^server-port=.*$/m, 'server-port=25629')
    .replace(/^server-ip=.*$/m, 'server-ip=127.0.0.1')
    .replace(/^online-mode=.*$/m, 'online-mode=false')
    .replace(/^level-name=.*$/m, 'level-name=world')
    .replace(/^view-distance=.*$/m, 'view-distance=2')
    .replace(/^simulation-distance=.*$/m, 'simulation-distance=2');
  fs.writeFileSync(path.join(run, 'server.properties'), properties);
  fs.copyFileSync(path.join(stage, 'eula.txt'), path.join(run, 'eula.txt'));
  const fmlPath = path.join(run, 'config', 'fml.toml');
  const fml = fs.readFileSync(fmlPath, 'utf8');
  if (!/^maxThreads\s*=.*$/m.test(fml)) throw Error('Missing maxThreads');
  fs.writeFileSync(fmlPath, fml.replace(/^maxThreads\s*=.*$/m, 'maxThreads = -1'));
  // All timing runs start from this same read-only world copy when supplied.
  if (process.env.COMPAT_WORLD) fs.cpSync(process.env.COMPAT_WORLD, path.join(run, 'world'), {recursive:true});
  if (fixed) {
    const jars = fs.readdirSync(path.join(root, 'build', 'libs')).filter(n => /^corgikubejscompat-.*\.jar$/.test(n));
    if (jars.length !== 1) throw Error('Expected exactly one release jar');
    fs.copyFileSync(path.join(root, 'build', 'libs', jars[0]), path.join(run, 'mods', jars[0]));
  }
  const args = ['-Xms1G', '-Xmx6G', '-Djava.awt.headless=true'];
  if (instrumented) args.push('-javaagent:' + path.join(root, 'build/verification/verification-agent.jar'), '-Dcompat.scenario=' + scenario);
  args.push('@libraries/net/minecraftforge/forge/1.20.1-47.4.10/win_args.txt', 'nogui');
  console.log('Starting', scenario, run);
  const started = performance.now();
  const child = cp.spawn(java, args, {cwd:run, windowsHide:true, stdio:['pipe','pipe','pipe']});
  try { os.setPriority(child.pid, os.constants.priority.PRIORITY_BELOW_NORMAL); }
  catch (error) { child.kill(); throw error; }
  fs.writeFileSync(path.join(run, 'pid.txt'), String(child.pid));
  const log = fs.createWriteStream(path.join(run, 'console.log'));
  let text = '', doneMs, reloadRequested = false, reloaded = false, stopping = false, expired = false;
  let reloadTimer;
  const stop = () => { if (!stopping) { stopping = true; child.stdin.write('stop\n'); } };
  const watchdog = setTimeout(() => { expired = true; child.kill(); }, 480000);
  const receive = data => {
    log.write(data);
    text += data.toString();
    if (!doneMs && /Done \([\d.]+s\)!/.test(text)) {
      doneMs = performance.now() - started;
      console.log('Done in', (doneMs / 1000).toFixed(3), 'seconds; requesting reload');
      reloadRequested = true;
      child.stdin.write('reload\n');
    }
    if (reloadRequested && !reloaded && (instrumented ? text.includes('COMPAT_RELOAD_COMPLETE')
      : /Loaded \d+ advancements/.test(text.slice(text.indexOf('Reloading!'))))) {
      reloaded = true;
      reloadTimer = setTimeout(stop, instrumented ? 1000 : 5000);
    }
  };
  child.stdout.on('data', receive);
  child.stderr.on('data', receive);
  child.stdin.on('error', () => {});
  const cleanup = () => { if (child.exitCode === null) child.kill(); };
  process.once('SIGINT', cleanup);
  process.once('SIGTERM', cleanup);
  const code = await new Promise((resolve, reject) => { child.once('error', reject); child.once('exit', resolve); });
  clearTimeout(watchdog); clearTimeout(reloadTimer);
  log.end();
  process.removeListener('SIGINT', cleanup); process.removeListener('SIGTERM', cleanup);
  const trace = instrumented ? fs.readFileSync(path.join(run, 'race-trace.log'), 'utf8') : '';
  const snapshots = fs.readdirSync(run).filter(n => /^registry-snapshot-\d+\.txt$/.test(n)).sort((a,b) => parseInt(a.match(/\d+/)[0])-parseInt(b.match(/\d+/)[0]));
  let contentsHash;
  if (instrumented && scenario !== 'control' && snapshots.length >= 3) {
    const before = fs.readFileSync(path.join(run, snapshots.at(-2)), 'utf8');
    const after = fs.readFileSync(path.join(run, snapshots.at(-1)), 'utf8');
    if (before !== after) throw Error('Registry contents changed across reload: ' + run);
    for (const name of ['blending_function', 'villager_trades_item_listing', 'condition']) {
      if (!after.split('\n').some(line => line.startsWith(`ResourceKey[minecraft:root / corgilib:${name}]=[`) && !line.endsWith('=[]'))) {
        throw Error('Missing codec contents: ' + name);
      }
    }
    contentsHash = crypto.createHash('sha256').update(after).digest('hex');
  }
  const passed = !expired && (scenario === 'control'
    ? trace.includes('PASS control') && /ConcurrentModificationException/.test(text) && /ScriptManager\.load\(ScriptManager\.java:192\)/.test(trace)
    : code === 0 && doneMs && reloaded && (!instrumented || (trace.includes('PASS ' + scenario) && contentsHash)));
  const result = {scenario, run, pid:child.pid, code, expired, doneMs, reloaded, contentsHash, passed};
  fs.writeFileSync(path.join(run, 'result.json'), JSON.stringify(result, null, 2));
  console.log(JSON.stringify(result));
  if (!passed) throw Error('Runtime verification invalid or failed; inspect ' + run);
}
main().catch(error => { console.error(error); process.exitCode = 1; });
