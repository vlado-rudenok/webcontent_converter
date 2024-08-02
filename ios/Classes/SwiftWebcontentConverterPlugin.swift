import Flutter
import UIKit
import WebKit

public class SwiftWebcontentConverterPlugin: NSObject, FlutterPlugin {
    var webView : WKWebView!
    var urlObservation: NSKeyValueObservation?
    public static func register(with registrar: FlutterPluginRegistrar) {
        let channel = FlutterMethodChannel(name: "webcontent_converter", binaryMessenger: registrar.messenger())
        let instance = SwiftWebcontentConverterPlugin()
        registrar.addMethodCallDelegate(instance, channel: channel)
        
        // binding native view to flutter widget
        let viewID = "webview-view-type"
        let factory = FLNativeViewFactory(messenger: registrar.messenger())
        registrar.register(factory, withId: viewID)
    }
    
    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        let method = call.method
        let arguments = call.arguments as? [String: Any]
        let content = arguments!["content"] as? String
        var duration = arguments!["duration"] as? Double
        if(duration==nil){ duration = 2000.0}
        switch method {
        case "contentToPDF":
            let path = arguments!["savedPath"] as? String
            let savedPath = URL.init(string: path!)?.path
            let format = arguments!["format"] as? Dictionary<String, Double>
            let footerText = arguments!["footerText"] as? String
            let headerText = arguments!["headerText"] as? String
            self.webView = WKWebView()
            self.webView.isHidden = false
            self.webView.tag = 100
            self.webView.loadHTMLString(content!, baseURL: Bundle.main.resourceURL)// load html into hidden webview
            urlObservation = webView.observe(\.isLoading, changeHandler: { (webView, change) in
                DispatchQueue.main.asyncAfter(deadline: .now() + (duration!/10000) ) {
                    print("height = \(self.webView.scrollView.contentSize.height)")
                    print("width = \(self.webView.scrollView.contentSize.width)")
                    guard let path = self.webView.exportAsPdfFromWebView(
                        savedPath: savedPath!, 
                        format: format!, 
                        footerText: footerText,
                        headerText: headerText) else {

                        result(nil)
                        return
                    }
                    result(path)
                    //dispose
                    self.dispose()
                }
            })
            break
        default:
            result("iOS " + UIDevice.current.systemVersion)
        }
        
    }
    
    func dispose() {
        //dispose
        if let viewWithTag = self.webView.viewWithTag(100) {
            viewWithTag.removeFromSuperview() // remove hidden webview when pdf is generated
            // clear WKWebView cache
            WKWebsiteDataStore.default().fetchDataRecords(ofTypes: WKWebsiteDataStore.allWebsiteDataTypes()) { records in
                records.forEach { record in
                    WKWebsiteDataStore.default().removeData(ofTypes: record.dataTypes, for: [record], completionHandler: {})
                }
            }
        }
        self.webView = nil
    }
}


// WKWebView extension for export web html content into pdf
extension WKWebView {
    
    // Call this function when WKWebView finish loading
    func exportAsPdfFromWebView(
        savedPath: String, 
        format: Dictionary<String, Double>, 
        footerText: String?,
        headerText: String?) -> String? {

        let width = CGFloat(format["width"] ?? 8.27).toPixel()
        let height = CGFloat(format["height"] ?? 11.27).toPixel()

        let page = CGRect(x: 0, y: 0, width: width, height: height )
        let printable = page.insetBy(dx: 0, dy: 0)
        let render = CustomPrintPageRenderer(headerText: headerText, footerText: footerText)
        render.addPrintFormatter(self.viewPrintFormatter(), startingAtPageAt: 0)
        render.setValue(NSValue(cgRect: page), forKey: "paperRect")
        render.setValue(NSValue(cgRect: printable), forKey: "printableRect")
        let pdfData = render.generatePdfData()
        let path = self.saveWebViewPdf(data: pdfData, savedPath: savedPath)
        return path
    }
    
    
    // Save pdf file in file document directory
    func saveWebViewPdf(data: NSMutableData, savedPath: String) -> String? {
        let url = URL.init(string: savedPath)!
        if data.write(toFile: savedPath, atomically: true) {
            return url.path
        } else {
            return nil
        }
    }
}

// used convert current inches value into real CGFloat
extension CGFloat{
    func toPixel() -> CGFloat {
        if(self>0){
            return self * 96
        }
        return 0
    }
}

class CustomPrintPageRenderer: UIPrintPageRenderer {
    
    var headerHeightValue: CGFloat
    var footerHeightValue: CGFloat

    let headerText: String?
    let footerText: String?

    init(headerText: String?, footerText: String?, headerHeightValue: CGFloat = 50.0, footerHeightValue: CGFloat = 50.0) {
        self.headerText = headerText
        self.footerText = footerText
        self.headerHeightValue = headerText != nil ? headerHeightValue : 0
        self.footerHeightValue = footerText != nil ? footerHeightValue : 0
    }
    
    override var headerHeight: CGFloat {
        get {
            headerHeightValue
        }
        set {
            headerHeightValue = newValue
        }
    }
    
    override var footerHeight: CGFloat {
        get {
            footerHeightValue
        }
        set {
            footerHeightValue = newValue
        }
    }
    override func drawHeaderForPage(at pageIndex: Int, in headerRect: CGRect) {
        guard let headerText else {
            super.drawHeaderForPage(at: pageIndex, in: headerRect)
            return
        }

        // Create paragraph style with center alignment and truncating tail
        let paragraphStyle = NSMutableParagraphStyle()
        paragraphStyle.alignment = .center
        paragraphStyle.lineBreakMode = .byTruncatingTail

        let attributes: [NSAttributedString.Key: Any] = [
            .font: UIFont.systemFont(ofSize: 12),
            .foregroundColor: UIColor.gray,
            .paragraphStyle: paragraphStyle
        ]

        // Calculate the size of the text
        let textSize = headerText.size(withAttributes: attributes)

        // Add padding (20px on both sides)
        let horizontalPadding: CGFloat = 20

        // Calculate the drawing rectangle centered within the headerRect with padding
        let textRect = CGRect(
            x: headerRect.origin.x + horizontalPadding,
            y: headerRect.midY - (textSize.height / 2),
            width: headerRect.width - 2 * horizontalPadding,
            height: textSize.height
        ).integral // Ensures the rectangle uses whole numbers for coordinates and size

        // Convert headerText to NSString to use draw method
        let nsHeaderText = headerText as NSString

        // Draw the text in the centered rect with truncation
        nsHeaderText.draw(in: textRect, withAttributes: attributes)
    }

    override func drawFooterForPage(at pageIndex: Int, in footerRect: CGRect) {
        guard let footerText else {
            super.drawFooterForPage(at: pageIndex, in: footerRect)
            return
        }
        let text = "\(footerText) \(pageIndex + 1)"
        let attributes = [
            NSAttributedString.Key.font: UIFont.systemFont(ofSize: 12),
            NSAttributedString.Key.foregroundColor: UIColor.darkGray
        ]

        let textSize = text.size(withAttributes: attributes)
        let textRect = CGRect(x: footerRect.midX - textSize.width / 2, y: footerRect.midY - textSize.height / 2, width: textSize.width, height: textSize.height)

        text.draw(in: textRect, withAttributes: attributes)
    }

    func generatePdfData() -> NSMutableData {
        let pdfData = NSMutableData()
        UIGraphicsBeginPDFContextToData(pdfData, self.paperRect, nil)
        self.prepare(forDrawingPages: NSMakeRange(0, self.numberOfPages))
        let printRect = UIGraphicsGetPDFContextBounds()
        for pdfPage in 0..<self.numberOfPages {
            UIGraphicsBeginPDFPage()
            self.drawPage(at: pdfPage, in: printRect)
        }
        UIGraphicsEndPDFContext();
        return pdfData
    }
}
