#!/usr/bin/env python3
"""Generates the official AHT Books app icons with the open book graphic and 'AHT Books' text below it.

Outputs:
- ios/AHTBooks/Resources/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png (1024x1024)
- store/play-store/icon-512.png (512x512)
- docs/icon-512.png (512x512)
"""
import pathlib
import subprocess
import shutil
from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parent.parent

SWIFT_RENDERER = """
import AppKit

let canvasSize = 1024
let size = NSSize(width: canvasSize, height: canvasSize)
let image = NSImage(size: size)

image.lockFocus()

// Background color #EEEAFF (238, 234, 255)
let bgColor = NSColor(red: 238/255.0, green: 234/255.0, blue: 255/255.0, alpha: 1.0)
bgColor.setFill()
NSRect(origin: .zero, size: size).fill()

guard let srcImage = NSImage(contentsOfFile: "%s") else { exit(1) }
let cropRect = NSRect(x: 94, y: 114, width: 323, height: 281)

let targetW: CGFloat = 323 * 1.82
let targetH: CGFloat = 281 * 1.82
let targetX = (CGFloat(canvasSize) - targetW) / 2.0

// Total group height = 511 + 65 + 115 = 691.
// Centered vertical distribution
let textY: CGFloat = 166
let targetY: CGFloat = 166 + 115 + 65

srcImage.draw(in: NSRect(x: targetX, y: targetY, width: targetW, height: targetH),
              from: cropRect,
              operation: .sourceOver,
              fraction: 1.0)

let text = "AHT Books"
guard let baseFont = NSFont.systemFont(ofSize: 118, weight: .bold).fontDescriptor.withDesign(.rounded),
      let font = NSFont(descriptor: baseFont, size: 118) else { exit(1) }

let paragraphStyle = NSMutableParagraphStyle()
paragraphStyle.alignment = .center

// Deep Indigo #3730A3
let textColor = NSColor(red: 55/255.0, green: 48/255.0, blue: 163/255.0, alpha: 1.0)

let attributes: [NSAttributedString.Key: Any] = [
    .font: font,
    .foregroundColor: textColor,
    .paragraphStyle: paragraphStyle,
    .kern: 2.0
]

let attrStr = NSAttributedString(string: text, attributes: attributes)
let textSize = attrStr.size()
let textX = (CGFloat(canvasSize) - textSize.width) / 2.0

attrStr.draw(at: NSPoint(x: textX, y: textY))

image.unlockFocus()

guard let tiffData = image.tiffRepresentation,
      let bitmap = NSBitmapImageRep(data: tiffData),
      let pngData = bitmap.representation(using: .png, properties: [:]) else { exit(1) }

try! pngData.write(to: URL(fileURLWithPath: "%s"))
"""

def main():
    src_icon = ROOT / "store/play-store/icon-512.png"
    tmp_swift = ROOT / "scripts" / "_render_icon.swift"
    out_1024 = ROOT / "ios/AHTBooks/Resources/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png"
    
    code = SWIFT_RENDERER % (str(src_icon), str(out_1024))
    tmp_swift.write_text(code)
    
    # Run Swift to generate 1024x1024
    subprocess.run(["swift", str(tmp_swift)], check=True)
    tmp_swift.unlink(missing_ok=True)
    print(f"Generated 1024x1024 icon: {out_1024}")
    
    # Generate 512x512 versions
    im_1024 = Image.open(out_1024)
    im_512 = im_1024.resize((512, 512), Image.Resampling.LANCZOS)
    
    store_512 = ROOT / "store/play-store/icon-512.png"
    docs_512 = ROOT / "docs/icon-512.png"
    
    im_512.save(store_512)
    im_512.save(docs_512)
    print(f"Updated 512x512 icon: {store_512}")
    print(f"Updated 512x512 icon: {docs_512}")

if __name__ == "__main__":
    main()
