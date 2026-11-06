#!/usr/bin/env python3
"""gameport-test.py <port> <sha1>: checks the game-port pack sniffer against a running test server.
HTTP GET/HEAD/404/405, a modern status ping (handshake + status request + ping), the legacy 0xFE ping, garbage bytes
and a client that sends nothing. Refuses the live ports. Run with python3 -I."""
import hashlib, json, socket, sys, time
port = int(sys.argv[1]); sha1 = sys.argv[2]
assert port not in (25565, 25575), 'live port'
HOST = '127.0.0.1'

def http(req, timeout=10):
    s = socket.create_connection((HOST, port), timeout=timeout)
    s.sendall(req.encode('latin-1'))
    data = b''
    while True:
        chunk = s.recv(65536)
        if not chunk:
            break
        data += chunk
    s.close()
    head, _, body = data.partition(b'\r\n\r\n')
    return head.decode('latin-1'), body

def varint(n):
    out = b''
    while True:
        b = n & 0x7f; n >>= 7
        out += bytes([b | (0x80 if n else 0)])
        if not n:
            return out

def read_varint(s):
    n = shift = 0
    while True:
        b = s.recv(1)[0]
        n |= (b & 0x7f) << shift; shift += 7
        if not b & 0x80:
            return n

def recv_exact(s, n):
    buf = b''
    while len(buf) < n:
        chunk = s.recv(n - len(buf))
        if not chunk:
            raise EOFError
        buf += chunk
    return buf

ok = True
def check(name, cond, detail=''):
    global ok
    ok &= bool(cond)
    print(('PASS ' if cond else 'FAIL ') + name + (' - ' + detail if detail else ''))

t0 = time.time()
head, body = http('GET /pack/%s.zip HTTP/1.1\r\nHost: x\r\nX-Minecraft-Username: gameport-test\r\n\r\n' % sha1)
check('GET 200', head.startswith('HTTP/1.1 200'), head.splitlines()[0])
check('GET sha1', hashlib.sha1(body).hexdigest() == sha1, '%d bytes in %d ms' % (len(body), (time.time() - t0) * 1000))
check('GET headers', 'Content-Type: application/zip' in head and ('Content-Length: %d' % len(body)) in head)
head, body = http('HEAD /pack/%s.zip HTTP/1.1\r\n\r\n' % sha1)
check('HEAD 200 no body', head.startswith('HTTP/1.1 200') and body == b'', head.splitlines()[0])
head, _ = http('GET /pack/deadbeef.zip HTTP/1.1\r\n\r\n')
check('wrong path 404', head.startswith('HTTP/1.1 404'), head.splitlines()[0])
head, _ = http('POST /pack/%s.zip HTTP/1.1\r\nContent-Length: 0\r\n\r\n' % sha1)
check('POST 405', head.startswith('HTTP/1.1 405'), head.splitlines()[0])

# modern status ping
s = socket.create_connection((HOST, port), timeout=10)
hs = varint(0) + varint(775) + varint(len(HOST)) + HOST.encode() + port.to_bytes(2, 'big') + varint(1)
s.sendall(varint(len(hs)) + hs + varint(1) + varint(0))
length = read_varint(s); pid = read_varint(s); strlen = read_varint(s)
status = json.loads(recv_exact(s, strlen))
s.sendall(varint(9) + varint(1) + (12345).to_bytes(8, 'big'))
length = read_varint(s); pid = read_varint(s); pong = int.from_bytes(recv_exact(s, 8), 'big')
s.close()
check('status ping', 'version' in status and pong == 12345, 'version=%s players=%s' % (status['version'].get('name'), status.get('players', {}).get('online')))

# legacy ping (1.6 style)
s = socket.create_connection((HOST, port), timeout=10)
s.sendall(b'\xfe\x01')
data = s.recv(4096)
s.close()
check('legacy ping', data[:1] == b'\xff', '%d bytes' % len(data))

# garbage: must reach Minecraft, which closes the connection; no HTTP answer
s = socket.create_connection((HOST, port), timeout=10)
s.sendall(b'\x16\x03\x01garbage-bytes-here')
try:
    data = s.recv(4096)
except (ConnectionResetError, socket.timeout):
    data = b''
s.close()
check('garbage not answered as HTTP', not data.startswith(b'HTTP/'), repr(data[:30]))

# silent client: nothing sent; after the sniff timeout the game pipeline owns it, a status ping then still works
s = socket.create_connection((HOST, port), timeout=40)
time.sleep(6)
s.sendall(varint(len(hs)) + hs + varint(1) + varint(0))
length = read_varint(s); pid = read_varint(s); strlen = read_varint(s)
status = json.loads(recv_exact(s, strlen))
s.close()
check('silent 6 s, then status ping', 'version' in status)
sys.exit(0 if ok else 1)
