function fn() {
  var env = karate.env || 'dev';
  karate.log('karate-config.js loaded, env:', env);
  return { configLoaded: true, env: env };
}
