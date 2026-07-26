import assert from 'node:assert/strict';
import test from 'node:test';
import { sanitizeRememberedLogin } from './remembered-login';

test('旧版记忆数据会移除明文密码', () => {
  const remembered = sanitizeRememberedLogin({
    userName: 'admin',
    password: 'plain-text-secret'
  });

  assert.deepEqual(remembered, { userName: 'admin' });
  assert.equal(Object.hasOwn(remembered || {}, 'password'), false);
});

test('用户名会被清理空格', () => {
  assert.deepEqual(sanitizeRememberedLogin({ userName: '  analyst  ' }), { userName: 'analyst' });
});

test('空用户名不会被持久化', () => {
  assert.equal(sanitizeRememberedLogin({ userName: '   ', password: 'secret' }), null);
  assert.equal(sanitizeRememberedLogin(null), null);
});
