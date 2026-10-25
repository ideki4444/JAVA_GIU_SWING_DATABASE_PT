#!/usr/bin/env python3
"""Build the complete PDF from current Markdown and local vector diagrams."""
from pathlib import Path
from html import escape
from urllib.parse import urlsplit,unquote
import re
from markdown_it import MarkdownIt
from weasyprint import HTML,default_url_fetcher
ROOT=Path(__file__).resolve().parents[2]
SOURCES=[ROOT/'README.md',ROOT/'docs/architecture.md',ROOT/'docs/verification.md']
PDF=ROOT/'docs/downloads/AquaTrack-Handbook.pdf'
BASE='https://github.com/ideki4444/JAVA_GIU_SWING_DATABASE_PT/blob/main/'
CSS='''
@page {size:A4;margin:18mm 16mm;@top-left{content:"AQUATRACK3-GPT-CLAUDE_1 / HANDBOOK";font:7pt sans-serif;color:#476b7c}@bottom-right{content:counter(page);font:8pt sans-serif}}
body{font:10pt/1.5 "DejaVu Sans",sans-serif;color:#18384a}h1,h2,h3{color:#12384e;break-after:avoid;line-height:1.2}h1{font-size:25pt}h2{font-size:17pt;border-bottom:1pt solid #bfdde0;margin-top:7mm}h3{font-size:12pt;margin-top:6mm}p{orphans:3;widows:3}a{color:#087f81;overflow-wrap:anywhere;text-decoration:none}table{border-collapse:collapse;width:100%;font-size:8pt;line-height:1.4;margin:4mm 0}th{background:#133d50;color:white}td,th{padding:2mm;border:.5pt solid #c5dce2;vertical-align:top;overflow-wrap:anywhere}tr{break-inside:avoid}tr:nth-child(even){background:#f0f7f8}th:first-child,td:first-child{min-width:26mm}thead{display:table-header-group}code{font-family:"DejaVu Sans Mono",monospace;font-size:.85em;overflow-wrap:anywhere}pre{white-space:pre-wrap;overflow-wrap:anywhere;font-size:8pt;background:#edf5f6;padding:3mm;border-left:2pt solid teal}figure{break-inside:avoid;margin:4mm 0}img{width:100%;max-height:185mm;object-fit:contain}.chapter,.toc{break-before:page}.toc a{display:block;font-size:9pt;padding:.8mm 0}.toc a::after{content:leader('.') target-counter(attr(href),page)}.cover{height:240mm;break-after:page}.cover h1{font-size:43pt;padding-top:35mm}.cover .strap{font-size:23pt;color:#087f81}.cover p{font-size:12pt}.cover .foot{margin-top:35mm;font-size:9pt}.level1{font-weight:bold;margin-top:4mm}
'''
def slug(s):return re.sub(r'\s','-',re.sub(r'[^\w\s-]','',s.lower().strip()))
def fetch(url,*a,**kw):
 u=urlsplit(url);p=Path(unquote(u.path)).resolve()
 if u.scheme!='file' or not p.is_relative_to(ROOT):raise ValueError('Only local repository resources allowed')
 return default_url_fetcher(url,*a,**kw)
def build():
 md=MarkdownIt('commonmark').enable('table');docs=[];toc=[];anchors=set();images=set()
 for n,path in enumerate(SOURCES):
  tokens=md.parse(path.read_text());seen={}
  for i,t in enumerate(tokens):
   if t.type=='heading_open':
    title=''.join(c.content for c in tokens[i+1].children if c.type in ('text','code_inline'));s=slug(title);count=seen.get(s,0);seen[s]=count+1
    anchor=f'd{n}-'+s+(f'-{count}' if count else '');t.attrSet('id',anchor);anchors.add(anchor)
    if t.tag in ('h1','h2'):toc.append((t.tag,title,anchor))
  docs.append((path,n,tokens))
 rendered=[]
 for path,n,tokens in docs:
  for t in tokens:
   for c in t.children or []:
    if c.type not in ('link_open','image'):continue
    key='href' if c.type=='link_open' else 'src';href=c.attrGet(key);u=urlsplit(href)
    if u.scheme:continue
    dest=(path.parent/unquote(u.path)).resolve() if u.path else path
    if not dest.exists() and dest!=PDF:raise ValueError(f'Missing local target {href} in {path}')
    if c.type=='image':images.add(dest);c.attrSet(key,dest.as_uri())
    elif dest in SOURCES:
     num=SOURCES.index(dest)
     anchor=f'd{num}-'+u.fragment if u.fragment else next(a for tag,title,a in toc if tag=='h1' and a.startswith(f'd{num}-'))
     if anchor not in anchors:raise ValueError(f'Missing anchor {anchor}')
     c.attrSet(key,'#'+anchor)
    else:c.attrSet(key,BASE+dest.relative_to(ROOT).as_posix()+('#'+u.fragment if u.fragment else ''))
  html=md.renderer.render(tokens,md.options,{})
  rendered.append('<section class="chapter">'+re.sub(r'<p>(<img[^>]+>)</p>',r'<figure>\1</figure>',html)+'</section>')
 contents=''.join(f'<a class="level{1 if tag=="h1" else 2}" href="#{a}">{escape(title)}</a>' for tag,title,a in toc)
 cover='<section class="cover"><p>JAVA SWING · JDBC · MYSQL</p><h1>AquaTrack</h1><p class="strap">Use the station system.<br>Understand its code.</p><p>Installation, every operational screen, worked examples, five infographics, and the design behind the uploaded NetBeans project.</p><p class="foot">Project: Aquatrack3-gpt-claude_1<br>Offline learning edition • Searchable text • Linked source references<br>Verification limitations and the known test mismatch are included.</p></section>'
 html='<!doctype html><html lang="en"><head><meta charset="utf-8"><title>AquaTrack3-gpt-claude_1 Handbook</title><style>'+CSS+'</style></head><body>'+cover+'<section class="toc"><h1>Contents</h1>'+contents+'</section>'+''.join(rendered)+'</body></html>'
 PDF.parent.mkdir(parents=True,exist_ok=True);doc=HTML(string=html,base_url=ROOT.as_uri()+'/',url_fetcher=fetch).render();doc.write_pdf(PDF)
 print(f'{len(doc.pages)} pages; {len(images)} embedded diagrams; {PDF.stat().st_size} bytes; local targets and anchors validated')
if __name__=='__main__':build()
