#!/usr/bin/env python3
"""pack-slowreader-test.py <port> <casino_sha1> <music_sha1> [stalled] [deadline_s]

Download pools and deadlines of the pack delivery (game port, or the own HTTP server with pack.serve: http-port)
against a running TEST server on 127.0.0.1. Run with python3 -I. Refuses the live ports.

  1. <stalled> (default 4 = the music pool) clients GET the music pack, read the head and 4 KiB, then stop reading
     (tiny receive buffer). They hold the music slots.
  2. One trickle client GETs the casino pack and reads 1 KiB/s (takes one casino slot).
  3. Meanwhile: one more music GET must get 503 (music pool full), and a normal casino download must get 200 with the
     right SHA-1 (the casino pack has its own pool).
  4. Every slow client must be cut by the server: on the game port with a reset (seen at once, also by a client that
     does not read: its socket leaves ESTABLISHED); the own HTTP server closes normally, which a non-reading client
     only sees after draining (reported as "seen after draining").
  5. Afterwards a normal music download must work again (slots free).
Expected cut times with the defaults: stalled ~15 s after the server's buffers filled (nothing accepted for 15 s),
trickle 15-25 s (stalled or below 32 KiB/s after 20 s).
"""
import hashlib, socket, sys, threading, time

if len(sys.argv) < 4:
    sys.exit(__doc__)
PORT = int(sys.argv[1]); CASINO = sys.argv[2].lower(); MUSIC = sys.argv[3].lower()
STALLED = int(sys.argv[4]) if len(sys.argv) > 4 else 4
DEADLINE = float(sys.argv[5]) if len(sys.argv) > 5 else 60.0
assert PORT not in (25565, 25575, 25580), 'live port'
HOST = '127.0.0.1'
T0 = time.time()
ok = True
lock = threading.Lock()


def log(msg):
    with lock:
        print('[%6.1f s] %s' % (time.time() - T0, msg), flush=True)


def check(name, cond, detail=''):
    global ok
    with lock:
        ok &= bool(cond)
        print(('PASS ' if cond else 'FAIL ') + name + (' - ' + detail if detail else ''), flush=True)


def request(sha, rcvbuf=None):
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    if rcvbuf:
        s.setsockopt(socket.SOL_SOCKET, socket.SO_RCVBUF, rcvbuf)
    s.settimeout(30)
    s.connect((HOST, PORT))
    s.sendall(('GET /pack/%s.zip HTTP/1.1\r\nHost: x\r\nX-Minecraft-Username: slowreader-test\r\n\r\n' % sha)
              .encode('latin-1'))
    return s


def read_head(s):
    data = b''
    while b'\r\n\r\n' not in data:
        chunk = s.recv(1)
        if not chunk:
            break
        data += chunk
    head = data.decode('latin-1')
    length = 0
    for line in head.split('\r\n'):
        if line.lower().startswith('content-length:'):
            length = int(line.split(':', 1)[1])
    return head, length


def tcp_state(s):
    try:
        return s.getsockopt(socket.IPPROTO_TCP, socket.TCP_INFO, 8)[0]  # tcpi_state; 1 = ESTABLISHED
    except OSError:
        return -1


def full_download(sha, name):
    t = time.time()
    s = request(sha)
    head, length = read_head(s)
    body = b''
    while len(body) < length:  # the own HTTP server keeps the connection open (keep-alive): read Content-Length
        chunk = s.recv(1 << 16)
        if not chunk:
            break
        body += chunk
    s.close()
    status = head.split('\r\n', 1)[0]
    good = status.startswith('HTTP/1.1 200') and hashlib.sha1(body).hexdigest() == sha
    check(name, good, '%s, %d/%d bytes, sha1 %s, %d ms' % (status, len(body), length,
          'ok' if hashlib.sha1(body).hexdigest() == sha else 'WRONG', (time.time() - t) * 1000))


results = {}


def drain(s, got, length):
    """Reads what is left; returns (how, bytes). A normal close behind queued data is only seen this way."""
    s.settimeout(10)
    try:
        while got < length:
            chunk = s.recv(1 << 16)
            if not chunk:
                return 'eof after draining', got
            got += len(chunk)
        return 'complete', got
    except ConnectionResetError:
        return 'reset while draining', got
    except socket.timeout:
        return 'still open', got


def stalled(i):
    name = 'stalled music reader #%d' % i
    s = request(MUSIC, rcvbuf=4096)
    head, length = read_head(s)
    if not head.startswith('HTTP/1.1 200'):
        results[name] = ('no slot', head.split('\r\n', 1)[0], 0)
        s.close()
        return
    got = len(s.recv(4096))
    start = time.time()
    log('%s: %s, read %d bytes, now stops reading' % (name, head.split('\r\n', 1)[0], got))
    while time.time() - T0 < DEADLINE:
        st = tcp_state(s)
        if st != 1:
            results[name] = ('reset', time.time() - start, got)
            log('%s: cut by the server after %.1f s (socket state %d)' % (name, time.time() - start, st))
            s.close()
            return
        time.sleep(0.25)
    # not seen while stalled (own HTTP server: normal close behind the queued data): drain and look for the end
    how, got = drain(s, got, length)
    results[name] = (how, 'the deadline (server log has the time)', got)
    log('%s: %s, %d/%d bytes' % (name, how, got, length))
    s.close()


def trickle():
    name = 'trickle casino reader (1 KiB/s)'
    s = request(CASINO, rcvbuf=4096)
    head, length = read_head(s)
    if not head.startswith('HTTP/1.1 200'):
        results[name] = ('no slot', head.split('\r\n', 1)[0], 0)
        s.close()
        return
    start = time.time(); got = 0
    log('%s: %s, reading 1 KiB/s' % (name, head.split('\r\n', 1)[0]))
    s.settimeout(5)
    while time.time() - T0 < DEADLINE:
        try:
            chunk = s.recv(1024)
        except ConnectionResetError:
            results[name] = ('reset', time.time() - start, got)
            break
        except socket.timeout:
            continue
        if not chunk:
            results[name] = ('eof' if got < length else 'complete', time.time() - start, got)
            break
        got += len(chunk)
        time.sleep(1)
    else:
        how, got = drain(s, got, length)
        results[name] = (how, 'the deadline (server log has the time)', got)
    secs = results[name][1]
    log('%s: %s after %s, %d/%d bytes' % (name, results[name][0], '%.1f s' % secs if isinstance(secs, float) else secs,
                                          got, length))
    s.close()


threads = [threading.Thread(target=stalled, args=(i + 1,)) for i in range(STALLED)]
threads.append(threading.Thread(target=trickle))
for t in threads:
    t.start()
    time.sleep(0.2)
time.sleep(2)

s = request(MUSIC)
head, _ = read_head(s)
s.close()
check('one more music GET while %d readers hold the music pool' % STALLED,
      STALLED < 4 or head.startswith('HTTP/1.1 503'), head.split('\r\n', 1)[0])
full_download(CASINO, 'casino pack downloads normally meanwhile')

for t in threads:
    t.join()
for name, (how, secs, got) in sorted(results.items()):
    if how == 'no slot':
        check(name + ' got a slot', False, secs)
        continue
    cut = how in ('reset', 'eof', 'eof after draining', 'reset while draining')
    detail = '%s after %s, %d bytes read' % (how, '%.1f s' % secs if isinstance(secs, float) else secs, got)
    check(name + ' cut by the server', cut, detail)
full_download(MUSIC, 'music pack downloads normally afterwards (slots free)')
sys.exit(0 if ok else 1)
