import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request

ROOT = Path(r'D:\VeinMinerPLUS')
WORK = Path(__file__).resolve().parent
REPO = 'GuHanDuRen/VeinMinerPLUS'
API = 'https://api.github.com/repos/' + REPO
TAG = 'v1.2.35'
JARS = [
    ('build/libs/veinminerplusG-1.21.1-neoforge-1.2.35.jar', 'C952FBBAF9A39A9B276537C1C1FD089F39D176782074949C6494A6B20C6BF495'),
    ('forge-1.20.1/build/libs/veinminerplusG-1.20.1-forge-1.2.23.jar', '0A14E950C1576FE6EAFB415D0FC3B6265F596C4FF2684655951561E1D315FDFB'),
    ('forge-1.12.2/build/libs/veinminerplusG-1.12.2-forge-1.2.21.jar', '8261B510F8B825A05EE76DF5C6E392211781AFFA913860F9403254B3DFD9DD91'),
]


def save(name, value):
    (WORK / name).write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding='utf-8')


def read(name):
    return json.loads((WORK / name).read_text(encoding='utf-8'))


def credential():
    token = os.environ.get('GH_TOKEN') or os.environ.get('GITHUB_TOKEN')
    if not token:
        env = dict(os.environ, GIT_TERMINAL_PROMPT='0', GCM_INTERACTIVE='never')
        result = subprocess.run(['git', 'credential', 'fill'], input='protocol=https\nhost=github.com\n\n',
                                capture_output=True, text=True, timeout=30, cwd=ROOT, env=env)
        if result.returncode:
            raise RuntimeError('Git credential helper failed')
        fields = dict(line.split('=', 1) for line in result.stdout.splitlines() if '=' in line)
        token = fields.get('password')
    if not token:
        raise RuntimeError('No GitHub credential available')
    return token


def api(path, method='GET', data=None, binary=False):
    url = path if path.startswith('https://') else API + path
    if urllib.parse.urlsplit(url).hostname not in ('api.github.com', 'uploads.github.com'):
        raise RuntimeError('Unexpected GitHub API host')
    headers = {'Authorization': 'Bearer ' + TOKEN, 'User-Agent': 'Codex-release-uploader',
               'Accept': 'application/vnd.github+json', 'X-GitHub-Api-Version': '2022-11-28'}
    if data is not None:
        headers['Content-Type'] = 'application/java-archive' if binary else 'application/json; charset=utf-8'
        if not binary:
            data = json.dumps(data, ensure_ascii=False).encode('utf-8')
    request = urllib.request.Request(url, data=data, headers=headers, method=method)
    with urllib.request.urlopen(request, timeout=45) as response:
        body = response.read()
    return json.loads(body) if body else None


def download(url):
    request = urllib.request.Request(url, headers={'User-Agent': 'Codex-release-uploader'})
    with urllib.request.urlopen(request, timeout=45) as response:
        return response.read()


def sha(data):
    return hashlib.sha256(data).hexdigest()


def local_jars():
    result = []
    for relative, expected in JARS:
        path = ROOT / relative
        data = path.read_bytes()
        if sha(data) != expected.lower():
            raise RuntimeError('Local artifact changed: ' + path.name)
        result.append((path, expected.lower(), data))
    return result


def prepare():
    release = api('/releases/tags/' + TAG)
    if release.get('immutable') or release['draft']:
        raise RuntimeError('Release is immutable or unexpectedly draft')
    if (WORK / 'original_release.json').exists():
        raise RuntimeError('Backup already exists; refusing to overwrite it')
    save('original_release.json', release)
    wanted = {p.name for p, _, _ in local_jars()}
    for asset in release['assets']:
        if asset['name'] in wanted:
            data = download(asset['browser_download_url'])
            expected = asset.get('digest')
            if len(data) != asset['size'] or (expected and expected != 'sha256:' + sha(data)):
                raise RuntimeError('Backup verification failed: ' + asset['name'])
            (WORK / asset['name']).write_bytes(data)
            print('Backed up:', asset['name'], len(data), sha(data), flush=True)
    print('Prepared release:', release['id'], release['tag_name'], flush=True)


def stage():
    original = read('original_release.json')
    current = api('/releases/' + str(original['id']))
    original_assets = {(a['id'], a['name'], a.get('digest')) for a in original['assets']}
    current_assets = {(a['id'], a['name'], a.get('digest')) for a in current['assets']}
    if original_assets != current_assets:
        raise RuntimeError('Release assets changed since backup')
    upload_url = current['upload_url'].split('{', 1)[0]
    staged = []
    for path, expected, data in local_jars():
        name = path.name + '.uploading-20261004-213147'
        asset = api(upload_url + '?' + urllib.parse.urlencode({'name': name}), method='POST', data=data, binary=True)
        staged.append({'original_name': path.name, 'expected_sha256': expected, 'asset': asset})
        save('staged_assets.json', staged)
        if asset['state'] != 'uploaded' or asset['size'] != len(data) or asset.get('digest') != 'sha256:' + expected:
            raise RuntimeError('Staged asset digest verification failed: ' + path.name)
        print('Uploaded and verified:', path.name, asset['id'], expected, flush=True)


def finalize():
    original = read('original_release.json')
    staged = read('staged_assets.json')
    if len(staged) != len(JARS):
        raise RuntimeError('Not all artifacts were staged')
    current = api('/releases/' + str(original['id']))
    if current['body'] != original['body'] or current['name'] != original['name']:
        raise RuntimeError('Release notes changed since backup')
    by_id = {a['id']: a for a in current['assets']}
    for entry in staged:
        asset = by_id[entry['asset']['id']]
        if asset['name'] != entry['asset']['name'] or asset['digest'] != 'sha256:' + entry['expected_sha256']:
            raise RuntimeError('Staged asset changed')
    renamed = []
    try:
        for entry in staged:
            existing = next((a for a in original['assets'] if a['name'] == entry['original_name']), None)
            if existing:
                if by_id[existing['id']]['name'] != existing['name']:
                    raise RuntimeError('Existing asset renamed by another operation')
                old_name = existing['name'] + '.previous-20261004-213147'
                api('/releases/assets/' + str(existing['id']), method='PATCH', data={'name': old_name})
                renamed.append(existing)
                save('renamed_originals.json', renamed)
            api('/releases/assets/' + str(entry['asset']['id']), method='PATCH', data={'name': entry['original_name']})
            print('Replaced:', entry['original_name'], flush=True)
        notes = (WORK / 'release_notes.md').read_text(encoding='utf-8')
        release = api('/releases/' + str(original['id']), method='PATCH', data={'body': notes})
        save('updated_release.json', release)
    except Exception:
        for entry in staged:
            api('/releases/assets/' + str(entry['asset']['id']), method='PATCH', data={'name': entry['asset']['name']})
        for old in renamed:
            api('/releases/assets/' + str(old['id']), method='PATCH', data={'name': old['name']})
        raise
    print('Release notes updated; previous assets retained temporarily for final verification.', flush=True)


def verify_and_clean():
    original = read('original_release.json')
    release = api('/releases/' + str(original['id']))
    assets = {a['name']: a for a in release['assets']}
    checks = []
    for path, expected, local in local_jars():
        asset = assets[path.name]
        remote = download(asset['browser_download_url'] + '?asset_id=' + str(asset['id']))
        if len(remote) != len(local) or sha(remote) != expected or asset.get('digest') != 'sha256:' + expected:
            raise RuntimeError('Public download verification failed: ' + path.name)
        checks.append({'name': path.name, 'id': asset['id'], 'size': len(remote), 'sha256': expected,
                       'download_url': asset['browser_download_url']})
        print('Public download verified:', path.name, expected, flush=True)
    save('verified_downloads.json', checks)
    for old in read('renamed_originals.json'):
        backup = WORK / old['name']
        if not backup.is_file() or (old.get('digest') and 'sha256:' + sha(backup.read_bytes()) != old['digest']):
            raise RuntimeError('Missing or invalid local backup')
        current = api('/releases/assets/' + str(old['id']))
        if current['name'] != old['name'] + '.previous-20261004-213147':
            raise RuntimeError('Unexpected old asset name; refusing deletion')
        api('/releases/assets/' + str(old['id']), method='DELETE')
        print('Removed backed-up previous attachment:', old['name'], flush=True)
    final = api('/releases/' + str(original['id']))
    save('final_release.json', final)
    expected_assets = {c['id'] for c in checks}
    unchanged_assets = {a['id'] for a in original['assets'] if a['name'] not in {p.name for p, _, _ in local_jars()}}
    if {a['id'] for a in final['assets']} != expected_assets | unchanged_assets:
        raise RuntimeError('Unexpected final release attachment list')
    print(json.dumps({'release': final['html_url'], 'assets': checks}, ensure_ascii=True), flush=True)


if __name__ == '__main__':
    try:
        TOKEN = credential()
        {'prepare': prepare, 'stage': stage, 'finalize': finalize, 'verify': verify_and_clean}[sys.argv[1]]()
    except urllib.error.HTTPError as error:
        print('GitHub request failed: HTTP', error.code, error.reason, file=sys.stderr)
        sys.exit(1)
