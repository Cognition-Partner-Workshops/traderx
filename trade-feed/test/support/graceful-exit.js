// Preloaded into the server child process during tests so that SIGTERM
// triggers a normal exit and V8 flushes coverage data for c8.
process.on('SIGTERM', () => process.exit(0));
