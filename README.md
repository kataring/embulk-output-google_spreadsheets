# Google Spreadsheets output plugin for Embulk [![Build Status](https://travis-ci.org/kataring/embulk-output-google_spreadsheets.svg?branch=master)](https://travis-ci.org/kataring/embulk-output-google_spreadsheets) [![Gem Version](https://badge.fury.io/rb/embulk-output-google_spreadsheets.svg)](http://badge.fury.io/rb/embulk-output-google_spreadsheets)

Embulk output plugin to load into Google Spreadsheets.

## Overview

* **Plugin type**: output
* **Load all or nothing**: no
* **Resume supported**: no
* **Cleanup supported**: yes

## Configuration

- **service_account_email**: description (string, required)
- **p12_keyfile**: description (string, required)
- **spreadsheet_id**: description (string, required)

## Example

```yaml
out:
  type: google_spreadsheets
  service_account_email: 'XXXXXXXXXXXXXXXXXXXXXXXX@developer.gserviceaccount.com'
  p12_keyfile: '/tmp/embulk.p12'
  spreadsheet_id: '1RPXaB85DXM7sGlpFYIcpoD2GWFpktgh0jBHlF4m1a0A'
```


## Build

```
$ ./gradlew gem
```
