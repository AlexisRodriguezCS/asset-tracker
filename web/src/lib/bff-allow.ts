/**
 * Which paths the BFF proxy will forward to the gateway, and nothing else.
 *
 * This is the whole of the proxy's authorization surface. The browser never
 * holds a token; it calls `/api/bff/<path>` and this app attaches the bearer
 * server-side. So a hole here is not "a page renders wrong" - it is an
 * authenticated proxy to any endpoint an attacker names, using the signed-in
 * user's credentials.
 *
 * It lives apart from the route handler so it can be tested without dragging in
 * `next/server` and `server-only`. The route imports it; nothing else should.
 */
export const ALLOW: RegExp[] = [
  /^assignments$/,
  /^assignments\/return(\?.*)?$/,
  /^assignments\/transfer$/,
  /^assignments\/offboard(\?.*)?$/,
  /^assignments\/event-requests$/,
  /^assignments\/event-requests\/\d+\/(approve|deny|fulfil)$/,
  /^assignments\/event-equipment(\?.*)?$/,
  /^assignments\/event-equipment\/\d+$/,
  /^assets$/,
  /^assets\/\d+$/,
  /^assets\/\d+\/status$/,
  /^assets\/types$/,
  /^assets\/types\/\d+(\?.*)?$/,
  /^people$/,
  /^people\/\d+\/(offboarding|departed|desk)$/,
  /^locations$/,
  /^clients$/,
];

/**
 * @param rel the joined path segments plus any query string, exactly as the
 *     route builds it - matching on the same string the proxy will forward
 */
export function isAllowedPath(rel: string): boolean {
  return ALLOW.some((re) => re.test(rel));
}
