// Read-only failure diagnostic for the exact Matrix JS SDK bundled by OpenClaw.
// The parent process supplies a disposable normal member session and never
// publishes this process's configuration or bearer.
const {createRequire} = require('node:module');

const token = process.env.WEAVE_MATRIX_MEMBER_TOKEN || '';
const packagePath = process.env.WEAVE_MATRIX_PLUGIN_PACKAGE || '';
const homeserver = process.env.WEAVE_MATRIX_HOMESERVER || '';
const userId = process.env.WEAVE_MATRIX_USER_ID || '';
if (!token || !packagePath || !homeserver || !userId) {
  process.stdout.write('matrixSdkProbe=missing-input\n');
  process.exit(0);
}

function safe(value) {
  return String(value || '')
    .replaceAll(token, '[redacted]')
    .replace(/[^A-Za-z0-9 .:_/-]/g, ' ')
    .slice(0, 180);
}

try {
  const sdk = createRequire(packagePath)('matrix-js-sdk');
  const client = sdk.createClient({baseUrl: homeserver, accessToken: token, userId});
  let done = false;
  function finish(detail) {
    if (done) return;
    done = true;
    process.stdout.write('matrixSdkProbe=' + safe(detail) + '\n');
    client.stopClient();
    process.exit(0);
  }
  client.on('sync', (state, previous, data) => {
    if (state === 'ERROR') {
      const error = data?.error;
      finish('error ' + (error?.errcode || error?.httpStatus || error?.name || '')
        + ' ' + (error?.message || error));
    } else if (state === 'PREPARED' || state === 'SYNCING') {
      finish('ready ' + state);
    }
  });
  setTimeout(() => finish('timeout-before-ready'), 20000).unref();
  Promise.resolve(client.startClient({initialSyncLimit: 1}))
    .catch((error) => finish('start-failed ' + (error?.message || error)));
} catch (error) {
  process.stdout.write('matrixSdkProbe=setup-failed ' + safe(error?.message || error) + '\n');
}
