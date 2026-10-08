// Ktor's JS engine imports `ws` only when it finds itself running under Node. In a browser
// that branch is never taken, but the bundler still has to resolve the name.
export default class WebSocketUnavailable {}
