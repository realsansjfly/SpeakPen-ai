"""Build the reviewed offline topic catalog from pinned CC0 text and original Chinese prompts."""
from pathlib import Path
import hashlib, json

root = Path(__file__).resolve().parents[1]
review = root / 'docs/research/topics-motion-2026-10-31'
out = root / 'app/src/main/assets/topics'
# Reviewed line allowlist. Excludes invasive/school-group-only questions and unhelpful fragments.
selected = '''3 15 18 25 26 27 32 33 37 38 41 44 49 54 58 60 63 65 66 69 70 75 76 78 80 84 87 88 90 92 96 97 98 99 100 104 105 106 108 109 110 111 112 114 116 125 133 135 142 145 146 147 160 161 162 168 169 170 171 172 173 174 175 176 177 178 179 181 183 184 185 186 187 188 189 190 191 192 193 194 195 196 197 198 199 200 201 202 203 204 205 209 210 211 212 217 218 219 220 221 222 223 224 225 226 228 229 231 234 235 236 237 238 239 240 241 242 243 244 248 252 253 255 256 258 262 263 264 265 267 268 272 276 279 280 281 282 283 291 297 301 303 308 309 310 313 320 324 332 339 342 344 345 355 356 357 359 360 362 367 368 371 376 377 381 388 392 393 394 398 404 410 412 413 414 428 439 440 441 442 445 447 448 451 452 453 455 456 457 458 459 460 461 462 463 464 466 467 468 469 470 472 475 476 477 478 479 480 481 482 483 484 485 486 487 488 489 490 499 502 504 505 506 508 513 514 519 523 524 526 528 529 531 532 534 537 538 555 556 558'''
lines = (review / 'cc0-questions.txt').read_text(encoding='utf-8-sig').splitlines()
items = [{'id':f'cc0-{i:03}', 'text':lines[i-1].strip(), 'source':'eecastro-cc0', 'line':i}
         for i in map(int, selected.split())]
original = (out / 'speakpen-zh.txt').read_text(encoding='utf-8').splitlines()
items += [{'id':f'sp-{i:03}', 'text':s.strip(), 'source':'speakpen-original'} for i,s in enumerate(original,1) if s.strip()]
assert len({x['text'].casefold() for x in items}) == len(items)
assert all(2 <= len(x['text']) <= 130 and '\n' not in x['text'] for x in items)
catalog = {'version':1, 'topics':items}
raw = (json.dumps(catalog,ensure_ascii=False,indent=2)+'\n').encode('utf-8')
(out/'catalog.json').write_bytes(raw)
(out/'CC0-LICENSE.txt').write_bytes((review/'cc0-LICENSE.txt').read_bytes())
(out/'NOTICE.txt').write_text('SpeakPen offline conversation topics\n\n'
 'English selection: eecastro/Icebreakers, CC0-1.0.\n'
 'Source: https://github.com/eecastro/Icebreakers/tree/e9bbcaa0e89fd7f44f2abd3fdead2e54c3cdb67c\n'
 'Original wording preserved; source line recorded per entry. Not a proficiency syllabus.\n'
 'Chinese prompts: original SpeakPen content (2026-10-31).\n', encoding='utf-8')
report={'version':1,'total':len(items),'english':len(items)-len(original),'chinese':len(original),
        'sha256':hashlib.sha256(raw).hexdigest(),'sourceCommit':'e9bbcaa0e89fd7f44f2abd3fdead2e54c3cdb67c'}
(out/'manifest.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
print(json.dumps(report))
