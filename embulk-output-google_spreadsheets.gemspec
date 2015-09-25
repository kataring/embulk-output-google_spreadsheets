
Gem::Specification.new do |spec|
  spec.name          = "embulk-output-google_spreadsheets"
  spec.version       = "0.1.0"
  spec.authors       = ["Noriaki Katayama"]
  spec.summary       = %[Google Spreadsheets output plugin for Embulk]
  spec.description   = %[Dumps records to Google Spreadsheets.]
  spec.email         = ["kataring@gmail.com"]
  spec.licenses      = ["MIT"]
  spec.homepage      = "https://github.com/kataring/embulk-output-google_spreadsheets"

  spec.files         = `git ls-files`.split("\n") + Dir["classpath/*.jar"]
  spec.test_files    = spec.files.grep(%r"^(test|spec)/")
  spec.require_paths = ["lib"]

  #spec.add_dependency 'YOUR_GEM_DEPENDENCY', ['~> YOUR_GEM_DEPENDENCY_VERSION']
  spec.add_development_dependency 'bundler', ['~> 1.0']
  spec.add_development_dependency 'rake', ['>= 10.0']
end
