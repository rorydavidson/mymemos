import SwiftUI
import Shared

// A first vertical slice of the macOS client: sign in through the shared Ktor client and
// list what comes back. The sync engine and the local database are still Android-only until
// core-data is ported, so nothing here is offline yet.

@MainActor
final class SessionModel: ObservableObject {
    enum Phase: Equatable {
        case signedOut
        case working(String)
        case signedIn
        case failed(String)
    }

    @Published var phase: Phase = .signedOut
    @Published var server = "https://memos.keltruc.com"
    @Published var username = ""
    @Published var password = ""
    @Published var memos: [MemoRow] = []
    @Published var serverVersion = ""
    @Published var displayName = ""

    private let session = MemosSession()

    var canSignIn: Bool {
        !server.isEmpty && !username.isEmpty && !password.isEmpty && phase != .working("")
    }

    func probe() async {
        phase = .working("Checking the server")
        do {
            serverVersion = try await session.probe(serverUrl: server)
            phase = .signedOut
        } catch {
            phase = .failed(readable(error))
        }
    }

    func signIn() async {
        phase = .working("Signing in")
        do {
            try await session.signIn(serverUrl: server, username: username, password: password)
            displayName = session.displayName
            password = ""
            await load()
        } catch {
            phase = .failed(readable(error))
        }
    }

    func load() async {
        phase = .working("Loading memos")
        do {
            memos = try await session.memos()
            phase = .signedIn
        } catch {
            phase = .failed(readable(error))
        }
    }

    /// Kotlin exceptions arrive as NSError with the Kotlin message on it.
    private func readable(_ error: Error) -> String {
        let nsError = error as NSError
        if let message = nsError.userInfo["KotlinException"] as? Error {
            return String(describing: message)
        }
        return nsError.localizedDescription
    }
}

struct SignInView: View {
    @ObservedObject var model: SessionModel

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("MyMemos").font(.system(size: 34, weight: .bold))
            Text("Connect to your Memos server").foregroundStyle(.secondary)

            TextField("Server URL", text: $model.server)
                .textFieldStyle(.roundedBorder)
                .onSubmit { Task { await model.probe() } }
            if !model.serverVersion.isEmpty {
                Label("Memos \(model.serverVersion)", systemImage: "checkmark.seal")
                    .font(.caption).foregroundStyle(.green)
            }
            TextField("Username", text: $model.username).textFieldStyle(.roundedBorder)
            SecureField("Password", text: $model.password)
                .textFieldStyle(.roundedBorder)
                .onSubmit { Task { await model.signIn() } }

            HStack {
                Button("Check server") { Task { await model.probe() } }
                Spacer()
                Button("Sign in") { Task { await model.signIn() } }
                    .keyboardShortcut(.defaultAction)
                    .disabled(!model.canSignIn)
            }

            if case let .working(what) = model.phase {
                HStack(spacing: 8) { ProgressView().controlSize(.small); Text(what).foregroundStyle(.secondary) }
            }
            if case let .failed(message) = model.phase {
                Text(message).font(.callout).foregroundStyle(.red).textSelection(.enabled)
            }
        }
        .padding(32)
        .frame(width: 460)
    }
}

struct MemoListView: View {
    @ObservedObject var model: SessionModel

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Memos").font(.title2.bold())
                    Text("\(model.displayName) · \(model.memos.count) memos · Memos \(model.serverVersion)")
                        .font(.caption).foregroundStyle(.secondary)
                }
                Spacer()
                Button { Task { await model.load() } } label: { Image(systemName: "arrow.clockwise") }
                    .disabled({ if case .working = model.phase { return true } else { return false } }())
            }
            .padding()
            Divider()

            List(model.memos, id: \.name) { memo in
                VStack(alignment: .leading, spacing: 6) {
                    HStack(spacing: 6) {
                        if memo.pinned { Image(systemName: "pin.fill").foregroundStyle(.orange) }
                        if memo.locked { Image(systemName: "lock.fill").foregroundStyle(.secondary) }
                        Text(memo.createdLabel).font(.caption).foregroundStyle(.secondary)
                    }
                    Text(memo.locked ? "Locked memo" : memo.content)
                        .font(.body)
                        .lineLimit(6)
                        .textSelection(.enabled)
                }
                .padding(.vertical, 6)
            }
        }
    }
}

struct RootView: View {
    @StateObject private var model = SessionModel()

    var body: some View {
        Group {
            if model.phase == .signedIn || !model.memos.isEmpty {
                MemoListView(model: model)
            } else {
                SignInView(model: model)
            }
        }
        .frame(minWidth: 460, minHeight: 520)
    }
}

@main
struct MyMemosApp: App {
    var body: some Scene {
        WindowGroup("MyMemos") { RootView() }
            .defaultSize(width: 640, height: 760)
    }
}
