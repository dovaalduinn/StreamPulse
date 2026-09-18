package com.example.sniffer

object BrowserSnifferScriptV2 {
    const val SNIFFER_JS = """
        (function() {
            if (window._pulseSnifferInjected) return;
            window._pulseSnifferInjected = true;
            
            var sbCounter = 0;
            var msCounter = 0;
            
            // Helper functions
            var cachedReporter = (window.AndroidSniffer && window.AndroidSniffer.onRawMediaEventJson) ? window.AndroidSniffer.onRawMediaEventJson.bind(window.AndroidSniffer) : null;
            function report(type, url, mediaType, title, duration, quality, mime, extraArgs) {
                if (!url || typeof url !== 'string') return;
                var data = {
                    sourceType: type,
                    url: url,
                    type: mediaType || 'Unknown',
                    title: title || '',
                    duration: duration || '',
                    quality: quality || '',
                    mimeType: mime || '',
                    headers: {},
                    topPageUrl: window.location.href !== window.top.location.href ? window.top.location.href : window.location.href,
                    iframeUrl: window.location.href !== window.top.location.href ? window.location.href : null
                };
                if (extraArgs) {
                    for (var key in extraArgs) {
                        data[key] = extraArgs[key];
                    }
                }
                try {
                    if (window.AndroidSniffer && window.AndroidSniffer.onRawMediaEventJson) {
                        window.AndroidSniffer.onRawMediaEventJson(JSON.stringify(data));
                    }
                } catch(e) {}
            }
            
            // 1. JSON scanning for escaped / relative media URLs
            function resolveUrl(url) {
                try { return new URL(url, window.location.href).href; } catch(e) { return url; }
            }
            function scanJsonForMedia(obj, depth) {
                if (depth > 5 || !obj) return;
                try {
                    if (Array.isArray(obj)) {
                        for (var i = 0; i < obj.length; i++) scanJsonForMedia(obj[i], depth + 1);
                    } else if (typeof obj === 'object') {
                        for (var key in obj) {
                            var val = obj[key];
                            if (typeof val === 'string') {
                                var k = key.toLowerCase();
                                var v = val.toLowerCase();
                                if (k.includes('video') || k.includes('media') || k.includes('stream') || k.includes('src') || k.includes('file') || k.includes('manifest') || k.includes('hls') || k.includes('dash') || k.includes('url') || k.includes('playback') || k.includes('source')) {
                                    if (v.startsWith('http') || v.startsWith('/')) {
                                        report("JSON", resolveUrl(val), "Unknown", "", "", "", "");
                                    }
                                } else if (v.includes('.m3u8') || v.includes('.mp4') || v.includes('.mpd') || v.includes('.ts') || v.includes('.m4s') || v.includes('.webm')) {
                                    if (v.startsWith('http') || v.startsWith('/')) {
                                        report("JSON", resolveUrl(val), "Unknown", "", "", "", "");
                                    }
                                }
                            } else if (typeof val === 'object') {
                                scanJsonForMedia(val, depth + 1);
                            }
                        }
                    }
                } catch(e) {}
            }

            // 2. Fetch Interceptor with Blob/ArrayBuffer correlation
            var origFetch = window.fetch;
            if (origFetch) {
                window.fetch = function() {
                    var reqUrl = arguments[0];
                    if (reqUrl && typeof reqUrl === 'object' && reqUrl.url) reqUrl = reqUrl.url;
                    reqUrl = resolveUrl(reqUrl);
                    var fetchArgs = arguments;
                    return origFetch.apply(this, fetchArgs).then(function(response) {
                        try {
                            var ct = (response.headers.get('Content-Type') || '').toLowerCase();
                            var cl = response.headers.get('Content-Length') || '';
                            var cr = response.headers.get('Content-Range') || '';
                            var u = resolveUrl(response.url || reqUrl || '');
                            
                            var extra = { initiatorType: 'fetch', byteLength: cl ? parseInt(cl) : null, contentRange: cr };
                            report("FETCH", u, "Unknown", "", "", "", ct, extra);
                            
                            // Attach context for arrayBuffer / blob resolving
                            var responseClone = response.clone();
                            response._pulseUrl = u;
                            response._pulseCt = ct;
                            
                            if (ct.includes('json') || ct.includes('text') || ct.includes('xml') || ct.includes('mpegurl') || ct.includes('dash+xml')) {
                                responseClone.text().then(function(text) {
                                    if (text.trim().startsWith('#EXTM3U')) {
                                        report("FETCH", u, "HLS", "", "", "", "application/vnd.apple.mpegurl", extra);
                                    } else if (text.includes('<MPD')) {
                                        report("FETCH", u, "Video", "", "", "", "application/dash+xml", extra);
                                    }
                                    if (ct.includes('json') && (text.startsWith('{') || text.startsWith('['))) {
                                        try { scanJsonForMedia(JSON.parse(text), 0); } catch(e){}
                                    } else {
                                        var urls = text.match(/https?:\/\/[^"'\s<>\\]+\.(?:m3u8|mp4|mpd|ts|m4s)[^"'\s<>\\]*/gi);
                                        if (urls) {
                                            Array.from(new Set(urls)).forEach(function(ur) { report("JSON", ur, "Unknown", "", "", "", ""); });
                                        }
                                    }
                                }).catch(function(e){});
                            }
                        } catch(e) {}
                        return response;
                    });
                };
            }
            
            // Hook Response ArrayBuffer/Blob to tag data with the URL
            if (window.Response && window.Response.prototype) {
                var origArrayBuffer = window.Response.prototype.arrayBuffer;
                if (origArrayBuffer) {
                    window.Response.prototype.arrayBuffer = function() {
                        var self = this;
                        return origArrayBuffer.apply(this, arguments).then(function(buf) {
                            if (buf && self._pulseUrl) {
                                buf._pulseUrl = self._pulseUrl;
                                buf._pulseCt = self._pulseCt;
                            }
                            return buf;
                        });
                    };
                }
            }

            // 3. XHR Interceptor
            var origXhrOpen = XMLHttpRequest.prototype.open;
            XMLHttpRequest.prototype.open = function() {
                try {
                    this._pulseReqUrl = resolveUrl(arguments[1] || '');
                    this.addEventListener('readystatechange', function() {
                        if (this.readyState === 2) {
                            var ct = (this.getResponseHeader('Content-Type') || '').toLowerCase();
                            var cl = this.getResponseHeader('Content-Length') || '';
                            var cr = this.getResponseHeader('Content-Range') || '';
                            var u = resolveUrl(this.responseURL || this._pulseReqUrl || '');
                            var extra = { initiatorType: 'xmlhttprequest', byteLength: cl ? parseInt(cl) : null, contentRange: cr, responseType: this.responseType };
                            report("XHR", u, "Unknown", "", "", "", ct, extra);
                            this._pulseCt = ct;
                            this._pulseUrl = u;
                        }
                    });
                    this.addEventListener('load', function() {
                        if (this.responseType === 'arraybuffer' && this.response) {
                            this.response._pulseUrl = this._pulseUrl;
                            this.response._pulseCt = this._pulseCt;
                        }
                        var ct = this._pulseCt || '';
                        if (this.responseType === '' || this.responseType === 'text') {
                            var text = this.responseText;
                            if (text) {
                                if (text.trim().startsWith('#EXTM3U')) {
                                    report("XHR", reqUrl, "HLS", "", "", "", "application/vnd.apple.mpegurl", { initiatorType: 'xmlhttprequest' });
                                } else if (text.includes('<MPD')) {
                                    report("XHR", reqUrl, "Video", "", "", "", "application/dash+xml", { initiatorType: 'xmlhttprequest' });
                                }
                                if (ct.includes('json') && (text.startsWith('{') || text.startsWith('['))) {
                                    try { scanJsonForMedia(JSON.parse(text), 0); } catch(e){}
                                }
                                var urls = text.match(/https?:\/\/[^"'\s<>\\]+\.(?:m3u8|mp4|mpd|ts|m4s)[^"'\s<>\\]*/gi);
                                if (urls) {
                                    Array.from(new Set(urls)).forEach(function(ur) { report("JSON", ur, "Unknown", "", "", "", ""); });
                                }
                            }
                        }
                    });
                } catch(e) {}
                origXhrOpen.apply(this, arguments);
            };

            // 4. MSE / SourceBuffer Interceptor
            if (window.MediaSource) {
                var origAddSourceBuffer = window.MediaSource.prototype.addSourceBuffer;
                window.MediaSource.prototype.addSourceBuffer = function(mime) {
                    var msId = this._pulseMsId || ("ms_" + (++msCounter));
                    this._pulseMsId = msId;
                    var sbId = "sb_" + (++sbCounter);
                    var sb = origAddSourceBuffer.apply(this, arguments);
                    if (!sb) return sb;
                    sb._pulseSbId = sbId;
                    sb._pulseMsId = msId;
                    try {
                        report("MSE", "mse://detected", "Unknown", "", "", "", mime, { isMse: true, mediaSourceId: msId });
                    } catch(e) {}
                    if (sb.appendBuffer) {
                        var origAppendBuffer = sb.appendBuffer;
                        sb.appendBuffer = function(data) {
                            try {
                                var correlatedUrl = data ? data._pulseUrl : null;
                                var ct = data ? data._pulseCt : null;
                                var bl = data ? data.byteLength : 0;
                                window._pulseMseBytes = (window._pulseMseBytes || 0) + bl;
                                
                                if (window._pulseMseBytes > 1024 * 1024 && !window._pulseMseReportedHighVolume) {
                                    window._pulseMseReportedHighVolume = true;
                                    report("MSE", window.location.href, "Video", "", "", "", mime || ct, { isMse: true, byteLength: window._pulseMseBytes, correlationContext: 'high_volume' });
                                }
                                
                                report("SOURCE_BUFFER", correlatedUrl || "sb://detected", "Unknown", "", "", "", mime || ct, { 
                                     isMse: true, 
                                     mediaSourceId: this._pulseMsId,
                                     sourceBufferId: this._pulseSbId,
                                     byteLength: bl,
                                     correlatedUrl: correlatedUrl
                                });
                            } catch(e) {}
                            return origAppendBuffer.apply(this, arguments);
                        };
                    }
                    return sb;
                };
            }

            // 5. Blob URL creation hook
            var origCreateObjectURL = URL.createObjectURL;
            URL.createObjectURL = function(obj) {
                var res = origCreateObjectURL.apply(this, arguments);
                try {
                    if (obj instanceof window.MediaSource) {
                        obj._pulseMsId = "ms_" + (++msCounter);
                        report("BLOB", res, "Unknown", "", "", "", "", { isMse: true, mediaSourceId: obj._pulseMsId });
                    }
                } catch(e) {}
                return res;
            };

            // 6. Performance Resource Timing
            setInterval(function() {
                try {
                    var resources = performance.getEntriesByType("resource");
                    for (var i = 0; i < resources.length; i++) {
                        var r = resources[i];
                        if (r._pulseReported) continue;
                        if (r.initiatorType === "video" || r.initiatorType === "audio" || r.initiatorType === "xmlhttprequest" || r.initiatorType === "fetch") {
                            r._pulseReported = true;
                            if (r.name.startsWith('blob:')) continue;
                            report("PERFORMANCE", r.name, "Unknown", "", "", "", "", { 
                                initiatorType: r.initiatorType,
                                transferSize: r.transferSize || 0,
                                duration: r.duration || 0
                            });
                        }
                    }
                } catch(e) {}
            }, 2000);
            
            // 8. WebSocket Interceptor
            var origWs = window.WebSocket;
            if (origWs) {
                window.WebSocket = function(url, protocols) {
                    var ws = new origWs(url, protocols);
                    try {
                        var resolvedWsUrl = resolveUrl(url);
                        report("WEBSOCKET", resolvedWsUrl, "Unknown", "", "", "", "", { initiatorType: 'websocket' });
                        ws.addEventListener('message', function(e) {
                            if (e.data instanceof ArrayBuffer || e.data instanceof Blob) {
                                var size = e.data.byteLength || e.data.size || 0;
                                if (size > 50000) { // If getting chunks larger than 50KB over WS, it's likely media
                                    report("WEBSOCKET", resolvedWsUrl, "Video", "", "", "", "application/octet-stream", { byteLength: size, initiatorType: 'websocket' });
                                }
                            }
                        });
                    } catch(e){}
                    return ws;
                };
                window.WebSocket.prototype = origWs.prototype;
            }
            
            // 9. Token / Custom Player Hook
            try {
                if (window.closeload || document.documentElement.innerHTML.includes("closeload")) {
                    // Try to extract setup vars from script tags
                    var scripts = document.getElementsByTagName('script');
                    for (var i = 0; i < scripts.length; i++) {
                        var text = scripts[i].innerText || scripts[i].textContent;
                        if (text && (text.includes('token') || text.includes('setup(') || text.includes('sources:'))) {
                             var matches = text.match(/https?:\/\/[^"'\s<>\\]+\.(?:m3u8|mp4|mpd)[^"'\s<>\\]*/gi);
                             if (matches) {
                                 Array.from(new Set(matches)).forEach(function(ur) { report("JSON", ur, "Unknown", "", "", "", "", { correlationContext: 'token_extraction' }); });
                             }
                        }
                    }
                }
            } catch(e){}
            
            // 7. Video tag hook (HTML5 Video)
            document.addEventListener('play', function(e) {
                if (e.target && e.target.tagName === 'VIDEO') {
                    var src = e.target.src || e.target.currentSrc;
                    if (src && !src.startsWith('blob:')) {
                        report("DOM", resolveUrl(src), "Unknown", "", "", "", "video/*");
                    }
                }
            }, true);

        })();
    """
}
