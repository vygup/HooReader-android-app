#!/usr/bin/env python3
"""Воспроизводимые синтетические книги; оригинальные тексты HooReader, без чужого контента."""
import base64
import hashlib
import json
from pathlib import Path
from zipfile import ZIP_STORED, ZipFile, ZipInfo

ROOT = Path(__file__).resolve().parents[1]
BOOKS = ROOT / 'app/src/androidTest/assets/books'
CORPUS = BOOKS / 'corpus'
PNG = base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGMwi24AAAHcARKsQ0g7AAAAAElFTkSuQmCC')
TEXT = 'Кириллица, café, naïve — «текст». HooReader corpus.'
rows = []


def register(name, expected, features):
    data = (BOOKS / name).read_bytes()
    rows.append(dict(file=name, expected=expected, features=features,
                     bytes=len(data), sha256=hashlib.sha256(data).hexdigest()))


def fb2(name, encoding='utf-8', metadata=True, body=None, media=PNG):
    title = f'<description><title-info><book-title>{name}</book-title><author><nickname>HooReader</nickname></author></title-info></description>' if metadata else ''
    content = body or f'<section><title><p>Глава</p></title><p>{TEXT}</p><p><strong>Bold <emphasis>italic</emphasis></strong></p><image l:href="#pic"/></section><section><p>Последний абзац.</p></section>'
    xml = f'<?xml version="1.0" encoding="{encoding}"?><FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0" xmlns:l="http://www.w3.org/1999/xlink">{title}<body>{content}</body><binary id="pic" content-type="image/png">{base64.b64encode(media).decode()}</binary></FictionBook>'
    if encoding == 'windows-1251':
        xml = xml.replace('café, naïve', 'cafe, naive')
    path = CORPUS / name
    if encoding == 'UTF-16BE':
        data = b'\xfe\xff' + xml.encode('utf-16-be')
    else:
        data = xml.encode(encoding)
    path.write_bytes(data)
    register(f'corpus/{name}', 'READY', f'FB2 {encoding}; metadata={metadata}; structured/media')


def epub(name, version='3.0', body=None, media=PNG, obfuscated=False):
    content = body or f'<h1>Глава</h1><p>{TEXT}</p><p><b>Bold <i>italic</i></b></p><ol><li>First</li><li>Second</li></ol><img src="pic.png" alt="Corpus image"/>'
    nav_item = '<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>' if version == '3.0' else '<item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>'
    spine = '<spine>' if version == '3.0' else '<spine toc="ncx">'
    opf = f'''<?xml version="1.0" encoding="UTF-8"?><package xmlns="http://www.idpf.org/2007/opf" version="{version}" unique-identifier="id"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="id">urn:hooreader:{name}</dc:identifier><dc:title>{name}</dc:title><dc:language>ru</dc:language><dc:creator>HooReader</dc:creator><meta property="dcterms:modified">2026-10-01T00:00:00Z</meta></metadata><manifest>{nav_item}<item id="one" href="one.xhtml" media-type="application/xhtml+xml"/><item id="two" href="two.xhtml" media-type="application/xhtml+xml"/><item id="pic" href="pic.png" media-type="image/png"/></manifest>{spine}<itemref idref="one"/><itemref idref="two"/></spine></package>'''
    nav = '''<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>Contents</title></head><body><nav epub:type="toc"><ol><li><a href="one.xhtml">Глава 1</a></li><li><a href="two.xhtml">Глава 2</a></li></ol></nav></body></html>'''
    ncx = '''<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1"><head><meta name="dtb:uid" content="corpus"/></head><docTitle><text>Corpus</text></docTitle><navMap><navPoint id="one" playOrder="1"><navLabel><text>Глава 1</text></navLabel><content src="one.xhtml"/></navPoint><navPoint id="two" playOrder="2"><navLabel><text>Глава 2</text></navLabel><content src="two.xhtml"/></navPoint></navMap></ncx>'''
    entries = {'mimetype': 'application/epub+zip', 'META-INF/container.xml': '<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0"><rootfiles><rootfile full-path="OPS/package.opf" media-type="application/oebps-package+xml"/></rootfiles></container>', 'OPS/package.opf': opf, 'OPS/nav.xhtml': nav, 'OPS/toc.ncx': ncx, 'OPS/one.xhtml': f'<html xmlns="http://www.w3.org/1999/xhtml"><head><title>One</title></head><body>{content}</body></html>', 'OPS/two.xhtml': '<html xmlns="http://www.w3.org/1999/xhtml"><head><title>Two</title></head><body><p>Последний абзац.</p></body></html>', 'OPS/pic.png': media}
    if obfuscated:
        entries['META-INF/encryption.xml'] = '<encryption xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><EncryptedData xmlns="http://www.w3.org/2001/04/xmlenc#"><EncryptionMethod Algorithm="http://www.idpf.org/2008/embedding"/><CipherData><CipherReference URI="OPS/font.otf"/></CipherData></EncryptedData></encryption>'
        entries['OPS/font.otf'] = b'Corpus font placeholder; never rendered'
    with ZipFile(CORPUS / name, 'w') as archive:
        for path, data in entries.items():
            info = ZipInfo(path, (1980, 1, 1, 0, 0, 0))
            info.compress_type = ZIP_STORED
            archive.writestr(info, data)
    register(f'corpus/{name}', 'READY', f'EPUB {version}; structured/media; font obfuscation={obfuscated}')


CORPUS.mkdir(parents=True, exist_ok=True)
for name in ('structured.epub', 'no-author-cover.epub', 'structured.fb2', 'windows-1251.fb2', 'missing-metadata.fb2'):
    register(name, 'READY', 'Baseline fixture: metadata/fallback/structure/encoding')
for name, encoding in (('utf8.fb2', 'utf-8'), ('utf16le.fb2', 'UTF-16'), ('utf16be.fb2', 'UTF-16BE'), ('cp1251.fb2', 'windows-1251')):
    fb2(name, encoding)
fb2('no-metadata.fb2', metadata=False)
fb2('nested.fb2', body=f'<section><title><p>Outer</p></title><section><p>{TEXT}</p></section></section><section><p>Next</p></section>')
fb2('broken-image.fb2', media=b'not an image')
epub('epub2.epub', version='2.0')
epub('epub3.epub')
epub('broken-image.epub', media=b'not an image')
epub('active-external.epub', body=f'<h1>Глава</h1><script>throw new Error("Never execute")</script><iframe src="https://example.invalid"/><p>{TEXT}</p><img src="https://example.invalid/pic.png"/>')
epub('font-obfuscation.epub', obfuscated=True)
for name, expected in (('empty.epub', 'EMPTY'), ('empty.fb2', 'EMPTY'), ('corrupt.epub', 'CORRUPT'), ('corrupt.fb2', 'CORRUPT'), ('drm-marker.epub', 'DRM'), ('unsupported.pdf', 'UNSUPPORTED_FORMAT')):
    register(name, expected, 'Negative control; excluded from valid-file denominator')
(CORPUS / 'xxe.fb2').write_text('<!DOCTYPE FictionBook [<!ENTITY leak SYSTEM "file:///data/data/com.hooreader/files/private">]><FictionBook><body><section><p>&leak;</p></section></body></FictionBook>')
register('corpus/xxe.fb2', 'CORRUPT', 'Negative control: external XML entity')
(CORPUS / 'manifest.json').write_text(json.dumps(rows, ensure_ascii=False, indent=2) + '\n')
print(f'Corpus: {sum(r["expected"] == "READY" for r in rows)} valid + {sum(r["expected"] != "READY" for r in rows)} negative')
