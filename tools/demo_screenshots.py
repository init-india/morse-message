"""Replace the real conversations in the two Messages screenshots with made-up demo chats.

Inputs are the original phone screenshots (not kept in the repo, they contain private messages);
outputs fastlane/.../phoneScreenshots/1.png (chats in Morse) and 2.png (chats in normal text)."""
from PIL import Image, ImageDraw, ImageFont

U = '/mnt/user-data/uploads/'
F = '/usr/share/fonts/opentype/inter/'
BG, TEXT, AVATAR, LETTER = (18, 38, 58), (230, 224, 233), (79, 55, 139), (231, 218, 253)
font = lambda w, s: ImageFont.truetype(F + f'Inter-{w}.otf', s)
title_r, title_b, snip_r, snip_b = font('Regular', 22), font('SemiBold', 22), font('Regular', 21), font('Medium', 21)
date_f, letter_f = font('SemiBold', 17), font('Medium', 26)

CODES = {**dict(zip("ABCDEFGHIJKLMNOPQRSTUVWXYZ", ".- -... -.-. -.. . ..-. --. .... .. .--- -.- .-.. -- -. --- .--. --.- .-. ... - ..- ...- .-- -..- -.-- --..".split())),
         **dict(zip("0123456789", "----- .---- ..--- ...-- ....- ..... -.... --... ---.. ----.".split()))}
def morse(text):   # English only, letters ' ', words ' / ', shown with · and −
    words = [" ".join(CODES[c] for c in w.upper() if c in CODES) for w in text.split()]
    return " / ".join(w for w in words if w).replace(".", "·").replace("-", "−")

# name, avatar letter (None = keep the person icon), plain snippet, snippet shown in abc mode, date, unread
CHATS = [
    ("Asha",        "A", "See you at 7 tonight",  "See you at 7 tonight",  "19/11/26", True),
    ("Ravi Kumar",  "R", "HAPPY BIRTHDAY",        "HAPPY BIRTHDAY",        "18/11/26", False),  # arrived in Morse
    ("Mum",         "M", "Call me when free",     "Call me when free",     "16/11/26", False),
    ("+1 555-0100", None, "Meeting moved to 3 pm", "Meeting moved to 3 pm", "12/11/26", False),
]
ROW_TOPS = [103, 207, 311, 415]

def fit(d, text, f, max_w):
    if d.textlength(text, font=f) <= max_w: return text
    while text and d.textlength(text + "…", font=f) > max_w: text = text[:-1]
    return text.rstrip() + "…"

def build(src, out, morse_mode):
    orig = Image.open(U + src).convert('RGB')
    im = orig.copy(); d = ImageDraw.Draw(im)
    d.rectangle((70, 2, 128, 38), fill=BG)                       # remove the redaction blob in the status bar
    for (name, letter, plain, abc, date, unread), top in zip(CHATS, ROW_TOPS):
        d.rectangle((0 if letter else 100, top + 1, 479, top + 102), fill=BG)
        cy = top + 51
        if letter:
            d.ellipse((23, cy - 33, 89, cy + 33), fill=AVATAR)
            d.text((56, cy), letter, font=letter_f, fill=LETTER, anchor="mm")
        d.text((108, top + 48), fit(d, name, title_b if unread else title_r, 265), font=title_b if unread else title_r, fill=TEXT, anchor="ls")
        snippet = morse(plain) if morse_mode else abc
        d.text((109, top + 77), fit(d, snippet, snip_b if unread else snip_r, 255), font=snip_b if unread else snip_r, fill=TEXT, anchor="ls")
        d.text((453, top + 57), date, font=date_f, fill=TEXT, anchor="rs")
    # put back the parts of the real UI that overlap the rows: mode button and New message button
    def paste_shape(box, radius):
        mask = Image.new('L', orig.size, 0)
        ImageDraw.Draw(mask).rounded_rectangle(box, radius=radius, fill=255)
        im.paste(orig, (0, 0), mask)
    paste_shape((385, 43, 454, 124), 13)    # morse / abc button
    paste_shape((213, 412, 455, 496), 17)   # New message button
    im.save(out, optimize=True)

build('7d8f675e-image.png', '1.png', morse_mode=True)    # chats shown in Morse
build('2b8d313d-image.png', '2.png', morse_mode=False)   # chats in normal text
print("ok")
