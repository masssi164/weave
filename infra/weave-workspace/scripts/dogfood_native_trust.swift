import Foundation
import Security
import CryptoKit

// Default OS evaluation remains authoritative. No private CA anchor is installed or supplied.
final class DirectTrustDelegate: NSObject, URLSessionTaskDelegate, @unchecked Sendable {
  let expectedFingerprint: String
  private let lock = NSLock()
  private var matchingChain = false
  var chainMatches: Bool { lock.lock(); defer { lock.unlock() }; return matchingChain }
  init(_ fingerprint: String) { expectedFingerprint = fingerprint }

  func urlSession(_ session: URLSession, task: URLSessionTask,
                  willPerformHTTPRedirection response: HTTPURLResponse,
                  newRequest request: URLRequest,
                  completionHandler: @escaping (URLRequest?) -> Void) {
    completionHandler(nil)
  }

  func urlSession(_ session: URLSession, task: URLSessionTask,
                  didReceive challenge: URLAuthenticationChallenge,
                  completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void) {
    if let trust = challenge.protectionSpace.serverTrust {
      if SecTrustEvaluateWithError(trust, nil),
         let chain = SecTrustCopyCertificateChain(trust) as? [SecCertificate],
         let root = chain.last {
        let fingerprint = SHA256.hash(data: SecCertificateCopyData(root) as Data)
          .map { String(format: "%02X", $0) }.joined(separator: ":")
        lock.lock(); matchingChain = fingerprint == expectedFingerprint; lock.unlock()
      }
    }
    completionHandler(.performDefaultHandling, nil)
  }
}

final class ProbeResult: @unchecked Sendable {
  private let lock = NSLock()
  private var value: [String: Any]
  init(_ origin: String) {
    value = ["origin": origin, "status": "incomplete", "category": "timeout", "nativeCode": -1001]
  }
  func store(_ observed: [String: Any]) { lock.lock(); value = observed; lock.unlock() }
  func snapshot() -> [String: Any] { lock.lock(); defer { lock.unlock() }; return value }
}

let arguments = Array(CommandLine.arguments.dropFirst())
guard arguments.count == 4,
      arguments[0].range(of: #"\A[0-9A-F]{2}(:[0-9A-F]{2}){31}\z"#, options: .regularExpression) != nil else {
  exit(2)
}
var checks: [[String: Any]] = []
for origin in arguments.dropFirst() {
  guard let url = URL(string: origin + "/"), url.scheme == "https", url.host != nil,
        url.user == nil, url.password == nil, url.query == nil, url.fragment == nil else { exit(2) }
  let configuration = URLSessionConfiguration.ephemeral
  configuration.connectionProxyDictionary = [:]
  configuration.urlCredentialStorage = nil
  configuration.httpCookieStorage = nil
  configuration.httpShouldSetCookies = false
  configuration.timeoutIntervalForRequest = 12
  configuration.timeoutIntervalForResource = 15
  let delegate = DirectTrustDelegate(arguments[0])
  let session = URLSession(configuration: configuration, delegate: delegate, delegateQueue: nil)
  let semaphore = DispatchSemaphore(value: 0)
  let result = ProbeResult(origin)
  let task = session.dataTask(with: url) { _, response, error in
    var observed: [String: Any] = ["origin": origin, "status": "incomplete", "category": "transport", "nativeCode": 0]
    if let error = error as? URLError {
      let code = error.errorCode
      observed["nativeCode"] = code
      observed["category"] = [-1202, -1203].contains(code) ? "tls-untrusted"
        : [-1201, -1204, -1205, -1206].contains(code) ? "tls-certificate-invalid"
        : code == -1200 ? "tls-handshake-failed" : code == -1003 ? "dns" : code == -1001 ? "timeout" : "transport"
    } else if let response = response as? HTTPURLResponse {
      observed["httpStatus"] = response.statusCode
      observed["category"] = delegate.chainMatches ? "trusted" : "tls-ca-mismatch"
      observed["status"] = delegate.chainMatches ? "passed" : "incomplete"
    }
    result.store(observed)
    semaphore.signal()
  }
  task.resume()
  _ = semaphore.wait(timeout: .now() + 18)
  task.cancel()
  session.invalidateAndCancel()
  checks.append(result.snapshot())
}
let payload: [String: Any] = ["schemaVersion": "weave.dogfood-native-trust-check/v1", "checks": checks,
                              "supportSafe": true, "containsSecretValues": false]
let data = try JSONSerialization.data(withJSONObject: payload, options: [.sortedKeys])
print(String(decoding: data, as: UTF8.self))
exit(checks.allSatisfy { $0["status"] as? String == "passed" } ? 0 : 2)
