import AppKit

guard CommandLine.arguments.count == 2 else {
    fputs("Usage: create-dmg-background.swift <output.png>\n", stderr)
    exit(2)
}

let size = NSSize(width: 1024, height: 640)
let image = NSImage(size: size)
image.lockFocus()

guard let context = NSGraphicsContext.current?.cgContext else {
    fputs("Could not create a graphics context for the installer background.\n", stderr)
    exit(1)
}

let colors = [
    NSColor(calibratedRed: 0.035, green: 0.055, blue: 0.11, alpha: 1).cgColor,
    NSColor(calibratedRed: 0.075, green: 0.13, blue: 0.24, alpha: 1).cgColor,
    NSColor(calibratedRed: 0.035, green: 0.075, blue: 0.16, alpha: 1).cgColor
] as CFArray
let locations: [CGFloat] = [0, 0.56, 1]
guard let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(),
                                colors: colors,
                                locations: locations) else {
    fputs("Could not create the installer background gradient.\n", stderr)
    exit(1)
}
context.drawLinearGradient(gradient,
                           start: CGPoint(x: 0, y: 0),
                           end: CGPoint(x: size.width, y: size.height),
                           options: [])

let glowColors = [
    NSColor(calibratedRed: 0.12, green: 0.39, blue: 0.95, alpha: 0.22).cgColor,
    NSColor(calibratedRed: 0.12, green: 0.39, blue: 0.95, alpha: 0).cgColor
] as CFArray
if let glow = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(),
                         colors: glowColors,
                         locations: [0, 1]) {
    context.drawRadialGradient(glow,
                               startCenter: CGPoint(x: 785, y: 330),
                               startRadius: 4,
                               endCenter: CGPoint(x: 785, y: 330),
                               endRadius: 320,
                               options: [])
}

func drawText(_ text: String, at point: CGPoint, font: NSFont, color: NSColor) {
    (text as NSString).draw(at: point, withAttributes: [
        .font: font,
        .foregroundColor: color
    ])
}

drawText("GAME TRACKER",
         at: CGPoint(x: 68, y: 548),
         font: .systemFont(ofSize: 34, weight: .bold),
         color: .white)
drawText("Your gaming history, all in one place.",
         at: CGPoint(x: 70, y: 518),
         font: .systemFont(ofSize: 17, weight: .regular),
         color: NSColor(calibratedWhite: 0.77, alpha: 1))

let divider = NSBezierPath()
divider.move(to: CGPoint(x: 70, y: 493))
divider.line(to: CGPoint(x: 954, y: 493))
divider.lineWidth = 1
NSColor(calibratedWhite: 1, alpha: 0.12).setStroke()
divider.stroke()

let arrow = NSBezierPath()
arrow.move(to: CGPoint(x: 365, y: 314))
arrow.line(to: CGPoint(x: 640, y: 314))
arrow.lineWidth = 9
arrow.lineCapStyle = .round
arrow.lineJoinStyle = .round

context.saveGState()
context.setShadow(offset: .zero,
                  blur: 20,
                  color: NSColor(calibratedRed: 0.24, green: 0.55, blue: 1, alpha: 0.85).cgColor)
NSColor(calibratedRed: 0.37, green: 0.69, blue: 1, alpha: 1).setStroke()
arrow.stroke()

let arrowHead = NSBezierPath()
arrowHead.move(to: CGPoint(x: 626, y: 346))
arrowHead.line(to: CGPoint(x: 666, y: 314))
arrowHead.line(to: CGPoint(x: 626, y: 282))
arrowHead.lineWidth = 9
arrowHead.lineCapStyle = .round
arrowHead.lineJoinStyle = .round
arrowHead.stroke()
context.restoreGState()

drawText("DRAG TO INSTALL",
         at: CGPoint(x: 445, y: 272),
         font: .systemFont(ofSize: 12, weight: .bold),
         color: NSColor(calibratedRed: 0.7, green: 0.84, blue: 1, alpha: 1))

drawText("Drop Game Tracker onto Applications to install",
         at: CGPoint(x: 308, y: 94),
         font: .systemFont(ofSize: 15, weight: .medium),
         color: NSColor(calibratedWhite: 0.82, alpha: 1))

image.unlockFocus()

guard let tiff = image.tiffRepresentation,
      let bitmap = NSBitmapImageRep(data: tiff),
      let png = bitmap.representation(using: .png, properties: [:]) else {
    fputs("Could not encode the installer background as PNG.\n", stderr)
    exit(1)
}

do {
    try png.write(to: URL(fileURLWithPath: CommandLine.arguments[1]), options: .atomic)
} catch {
    fputs("Could not save the installer background: \(error.localizedDescription)\n", stderr)
    exit(1)
}
