Feature: modern javascript in feature files

# the syntax karate-js 2.x supports - see KarateJsModernSyntaxTest for the unit-level coverage.
# these blocks must highlight without error annotations on the fallback (no IntelliJ JS plugin) path

  Scenario: optional chaining and nullish operators
    * def config = { server: { host: 'localhost' } }
    * def host = config?.server?.host
    * def port = config?.server?.['port']
    * def missing = config.absent?.deep
    * def fallback =
    """
      function fn(opts) {
        var timeout = opts?.timeout ?? 30
        var retries = opts?.retry?.count ?? 0
        opts ??= {}
        opts.name ||= 'default'
        opts.ready &&= true
        return { timeout: timeout, retries: retries, name: opts.name }
      }
    """
    * def called = karate.get('handler')?.(1, 2)

  Scenario: classes
    * def Builder =
    """
      class Builder {
        version = 1

        constructor(base) {
          this.base = base
        }

        static of(base) {
          return new Builder(base)
        }

        get url() {
          return this.base + '/api'
        }

        set url(value) {
          this.base = value
        }

        path(segment) {
          return this.url + '/' + segment
        }
      }
    """
    * def AuthBuilder =
    """
      class AuthBuilder extends Builder {
        constructor(base, token) {
          super(base)
          this.token = token
        }

        headers() {
          return { Authorization: this.token, class: 'auth', default: true }
        }
      }
    """
    * def anonymous =
    """
      var Anonymous = class { run() { return 1 } }
      new Anonymous().run()
    """

  Scenario: continue, void and bigint
    * def loop =
    """
      function fn(items) {
        var kept = []
        for (var i = 0; i < items.length; i++) {
          if (items[i] == null) {
            continue
          }
          kept.push(items[i])
        }
        return kept
      }
    """
    * def nothing = void 0
    * def big = 9007199254740993n
    * def bigHex = 0xffn
    * def bigSum = 1n + 2n
