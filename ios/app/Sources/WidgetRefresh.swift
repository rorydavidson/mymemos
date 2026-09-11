import WidgetKit

/// Tells WidgetKit the snapshot changed. Its own file so the shared model does not import
/// WidgetKit, which the Mac build has no use for.
enum WidgetRefresh {
    static func reload() {
        WidgetCenter.shared.reloadAllTimelines()
    }
}
