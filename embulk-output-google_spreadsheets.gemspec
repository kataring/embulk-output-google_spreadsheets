Gem::Specification.new do |spec|
  spec.name          = "embulk-output-google_spreadsheets"
  spec.version       = "0.1.0"
  spec.authors       = ["potato2003"]
  spec.summary       = "Google Spreadsheets output plugin for Embulk"
  spec.description   = "Dumps records to Google Spreadsheets."
  spec.email         = ["potato2003@gmail.com"]
  spec.licenses      = ["MIT"]
  # TODO set this: spec.homepage      = "https://github.com/ryuji.ito/embulk-output-google_spreadsheets"

  spec.files         = `git ls-files`.split("\n") + Dir["classpath/*.jar"]
  spec.test_files    = spec.files.grep(%r{^(test|spec)/})
  spec.require_paths = ["lib"]

  #spec.add_dependency 'YOUR_GEM_DEPENDENCY', ['~> YOUR_GEM_DEPENDENCY_VERSION']
  spec.add_development_dependency 'embulk', ['>= 0.8.35']
  spec.add_development_dependency 'bundler', ['>= 1.10.6']
  spec.add_development_dependency 'rake', ['>= 10.0']
  spec.add_dependency 'google-api-client'
end
