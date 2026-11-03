from pathlib import Path
import hashlib,json,urllib.request
r=Path(__file__).resolve().parent.parent;dest=r/'.local/java-deps';dest.mkdir(parents=True,exist_ok=True)
for item in json.loads((r/'scripts/java-dependencies.json').read_text(encoding='utf-8')):
 path=dest/item['name']
 if not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest()!=item['sha256']:
  data=urllib.request.urlopen(item['url'],timeout=60).read()
  if hashlib.sha256(data).hexdigest()!=item['sha256']:raise RuntimeError('Java dependency hash mismatch: '+item['name'])
  temp=path.with_suffix('.download');temp.write_bytes(data);temp.replace(path)
 print(item['name'])
