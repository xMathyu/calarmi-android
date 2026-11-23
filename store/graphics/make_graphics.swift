// make_graphics.swift — genera los gráficos de la ficha de Google Play:
//
//   icon-512.png                 512×512, PNG de 32 bits (RGBA, totalmente opaco).
//                                Play le pone él mismo las esquinas redondeadas y la sombra.
//   feature-graphic-1024x500.png 1024×500, PNG de 24 bits sin canal alfa (Play no acepta alfa aquí),
//                                con el lema en español. Más las variantes -en, -pt y -de, para
//                                subirlas como gráfico localizado en esas fichas.
//
// Uso, desde la raíz del repo de Android:
//
//   swift store/graphics/make_graphics.swift [ruta/a/light.svg]
//
// Por defecto toma el ícono de la app de iOS (Calarm/Tools/icon/light.svg), que es la fuente
// de verdad del ícono. La tipografía es Nunito (la de los titulares de la web, licencia OFL),
// incluida en fonts/ para que el resultado no dependa de lo instalado en la Mac.

import AppKit
import CoreText
import ImageIO
import UniformTypeIdentifiers

let here = URL(fileURLWithPath: #filePath).deletingLastPathComponent()
let svgPath = CommandLine.arguments.count > 1
    ? CommandLine.arguments[1]
    : "/Users/mathyu/repos/xMathyu/calarm/Calarm/Tools/icon/light.svg"

/// Lema de la imagen destacada por idioma (sufijo del archivo → texto). El salto de línea es a
/// mano para que el corte quede donde se lee mejor. "" es el archivo principal, en español.
let taglines: [(suffix: String, text: String)] = [
    ("", "Alarmas que suenan aunque\ntu teléfono esté en silencio"),
    ("-en", "Alarms that ring even when\nyour phone is on silent"),
    ("-pt", "Alarmes que tocam mesmo\ncom o celular no silencioso"),
    ("-de", "Wecker, die auch klingeln,\nwenn dein Handy lautlos ist"),
]

// MARK: - Utilidades

func rgb(_ hex: UInt32, _ alpha: CGFloat = 1) -> CGColor {
    CGColor(srgbRed: CGFloat((hex >> 16) & 0xFF) / 255,
            green: CGFloat((hex >> 8) & 0xFF) / 255,
            blue: CGFloat(hex & 0xFF) / 255,
            alpha: alpha)
}

let sRGB = CGColorSpace(name: CGColorSpace.sRGB)!

func makeContext(width: Int, height: Int, alpha: Bool) -> CGContext {
    let info = alpha ? CGImageAlphaInfo.premultipliedLast.rawValue : CGImageAlphaInfo.noneSkipLast.rawValue
    return CGContext(data: nil, width: width, height: height, bitsPerComponent: 8, bytesPerRow: 0,
                     space: sRGB, bitmapInfo: info)!
}

func save(_ ctx: CGContext, _ name: String) {
    let url = here.appendingPathComponent(name)
    let dest = CGImageDestinationCreateWithURL(url as CFURL, UTType.png.identifier as CFString, 1, nil)!
    CGImageDestinationAddImage(dest, ctx.makeImage()!, nil)
    guard CGImageDestinationFinalize(dest) else { fatalError("no pude escribir \(url.path)") }
    print("✓ \(name)")
}

guard let svg = NSImage(contentsOf: URL(fileURLWithPath: svgPath)) else {
    fatalError("no pude leer el SVG en \(svgPath)")
}

/// Dibuja el SVG del ícono ocupando `rect` (coordenadas de Core Graphics, origen abajo a la izquierda).
func drawIcon(in ctx: CGContext, rect: CGRect) {
    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.current = NSGraphicsContext(cgContext: ctx, flipped: false)
    svg.draw(in: rect)
    NSGraphicsContext.restoreGraphicsState()
}

/// Nunito con el peso indicado (eje variable `wght`, 200–1000).
func nunito(size: CGFloat, weight: CGFloat) -> CTFont {
    let url = here.appendingPathComponent("fonts/nunito-latin-wght-normal.woff2")
    guard let data = try? Data(contentsOf: url),
          let base = CTFontManagerCreateFontDescriptorFromData(data as CFData) else {
        fatalError("no pude cargar \(url.path)")
    }
    let wght = 0x7767_6874 // 'wght'
    let desc = CTFontDescriptorCreateCopyWithAttributes(base, [
        kCTFontVariationAttribute: [wght: weight],
    ] as CFDictionary)
    return CTFontCreateWithFontDescriptor(desc, size, nil)
}

func text(_ string: String, font: CTFont, color: CGColor, tracking: CGFloat = 0) -> NSAttributedString {
    let para = NSMutableParagraphStyle()
    para.lineHeightMultiple = 1.0
    return NSAttributedString(string: string, attributes: [
        NSAttributedString.Key(kCTFontAttributeName as String): font,
        NSAttributedString.Key(kCTForegroundColorAttributeName as String): color,
        NSAttributedString.Key(kCTKernAttributeName as String): tracking * CTFontGetSize(font),
        .paragraphStyle: para,
    ])
}

// MARK: - Ícono 512×512

do {
    let size = 512
    let ctx = makeContext(width: size, height: size, alpha: true)
    ctx.setFillColor(rgb(0xFFFFFF))
    ctx.fill(CGRect(x: 0, y: 0, width: size, height: size)) // alfa = 255 en todos los píxeles
    drawIcon(in: ctx, rect: CGRect(x: 0, y: 0, width: size, height: size))
    save(ctx, "icon-512.png")
}

// MARK: - Imagen destacada 1024×500

for (suffix, tagline) in taglines {
    let W: CGFloat = 1024, H: CGFloat = 500
    let ctx = makeContext(width: Int(W), height: Int(H), alpha: false)

    // Fondo: el mismo degradado de marca que el ícono, de arriba a la izquierda a abajo a la derecha.
    let brand = CGGradient(colorsSpace: sRGB, colors: [rgb(0xFFA066), rgb(0xEE4E1B)] as CFArray, locations: [0, 1])!
    ctx.drawLinearGradient(brand, start: CGPoint(x: 0, y: H), end: CGPoint(x: W * 0.75, y: -H * 0.35),
                           options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
    // Brillo suave arriba a la izquierda, como en el ícono.
    let glow = CGGradient(colorsSpace: sRGB, colors: [rgb(0xFFFFFF, 0.24), rgb(0xFFFFFF, 0)] as CFArray, locations: [0, 1])!
    ctx.drawRadialGradient(glow, startCenter: CGPoint(x: W * 0.12, y: H * 0.95), startRadius: 0,
                           endCenter: CGPoint(x: W * 0.12, y: H * 0.95), endRadius: W * 0.55, options: [])

    // Dos anillos tenues en la esquina de abajo a la derecha, como ondas de una alarma que suena.
    // Quedan por debajo del lema para no ensuciar el texto.
    let corner = CGPoint(x: W + 10, y: -90)
    ctx.setStrokeColor(rgb(0xFFFFFF, 0.11))
    ctx.setLineWidth(26)
    ctx.strokeEllipse(in: CGRect(x: corner.x - 150, y: corner.y - 150, width: 300, height: 300))
    ctx.setStrokeColor(rgb(0xFFFFFF, 0.06))
    ctx.setLineWidth(18)
    ctx.strokeEllipse(in: CGRect(x: corner.x - 225, y: corner.y - 225, width: 450, height: 450))

    // Ícono: baldosa redondeada con sombra, a la izquierda.
    let tile: CGFloat = 270
    let tileRect = CGRect(x: 92, y: (H - tile) / 2, width: tile, height: tile)
    let tilePath = CGPath(roundedRect: tileRect, cornerWidth: tile * 0.225, cornerHeight: tile * 0.225, transform: nil)
    ctx.saveGState()
    ctx.setShadow(offset: CGSize(width: 0, height: -16), blur: 40, color: rgb(0x7A1E00, 0.45))
    ctx.addPath(tilePath)
    ctx.setFillColor(rgb(0xF26A30))
    ctx.fillPath()
    ctx.restoreGState()
    ctx.saveGState()
    ctx.addPath(tilePath)
    ctx.clip()
    drawIcon(in: ctx, rect: tileRect)
    ctx.restoreGState()
    // Filo claro para separar la baldosa del fondo, que es del mismo color.
    ctx.addPath(tilePath)
    ctx.setStrokeColor(rgb(0xFFFFFF, 0.35))
    ctx.setLineWidth(2)
    ctx.strokePath()

    // Texto: nombre y lema.
    let textX = tileRect.maxX + 54
    let textW = W - textX - 70
    let nameFont = nunito(size: 118, weight: 900)
    let tagFont = nunito(size: 37, weight: 800)
    let name = text("Calarmi", font: nameFont, color: rgb(0xFFFFFF), tracking: -0.03)
    let tag = text(tagline, font: tagFont, color: rgb(0xFFFFFF, 0.94), tracking: -0.01)

    let tagSetter = CTFramesetterCreateWithAttributedString(tag)
    let tagSize = CTFramesetterSuggestFrameSizeWithConstraints(tagSetter, CFRange(), nil,
                                                               CGSize(width: textW, height: .greatestFiniteMagnitude), nil)
    let capH = CTFontGetCapHeight(nameFont)
    let gap: CGFloat = 26
    // Altura visual del bloque: de la línea de mayúsculas del nombre al final del lema.
    let block = capH + gap + tagSize.height
    let top = (H + block) / 2 + 4 // un poco arriba del centro geométrico se ve centrado
    let nameBaseline = top - capH

    ctx.textPosition = CGPoint(x: textX - 4, y: nameBaseline)
    CTLineDraw(CTLineCreateWithAttributedString(name), ctx)

    let tagRect = CGRect(x: textX, y: nameBaseline - gap - tagSize.height,
                         width: textW, height: ceil(tagSize.height))
    let frame = CTFramesetterCreateFrame(tagSetter, CFRange(), CGPath(rect: tagRect, transform: nil), nil)
    CTFrameDraw(frame, ctx)

    save(ctx, "feature-graphic-1024x500\(suffix).png")
}
