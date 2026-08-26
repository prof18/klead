import UIKit
import KleadBenchmarkRunner
import Darwin

@main
final class AppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?
    private var benchmarkRunner: IosDeviceBenchmarkRunner?
    private var didStartBenchmark = false

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        let viewController = UIViewController()
        viewController.view.backgroundColor = .systemBackground

        let statusLabel = UILabel()
        statusLabel.translatesAutoresizingMaskIntoConstraints = false
        statusLabel.font = .monospacedSystemFont(ofSize: 15, weight: .medium)
        statusLabel.text = "Klead Release Benchmark\nPreparing fixtures…"
        statusLabel.numberOfLines = 0
        statusLabel.textAlignment = .center
        viewController.view.addSubview(statusLabel)
        NSLayoutConstraint.activate([
            statusLabel.leadingAnchor.constraint(greaterThanOrEqualTo: viewController.view.leadingAnchor, constant: 24),
            statusLabel.trailingAnchor.constraint(lessThanOrEqualTo: viewController.view.trailingAnchor, constant: -24),
            statusLabel.centerXAnchor.constraint(equalTo: viewController.view.centerXAnchor),
            statusLabel.centerYAnchor.constraint(equalTo: viewController.view.centerYAnchor),
        ])

        let window = UIWindow(frame: UIScreen.main.bounds)
        window.rootViewController = viewController
        window.makeKeyAndVisible()
        self.window = window
        application.isIdleTimerDisabled = true

        // Let UIKit finish the launch transaction before starting CPU-heavy work.
        DispatchQueue.main.async { [weak self, weak statusLabel] in
            statusLabel?.text = "Klead Release Benchmark\nRunning measured passes…"
            self?.startBenchmark()
        }
        return true
    }

    private func startBenchmark() {
        guard !didStartBenchmark else { return }
        didStartBenchmark = true

        let runner = IosDeviceBenchmarkRunner()
        benchmarkRunner = runner
        runner.start { output, error in
            if let output {
                print(output)
            }
            if let error {
                print("KLEAD_BENCHMARK_ERROR\n\(error)")
            }
            fflush(stdout)

            DispatchQueue.main.async {
                Darwin.exit(error == nil ? EXIT_SUCCESS : EXIT_FAILURE)
            }
        }
    }
}
