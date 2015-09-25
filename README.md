# Google Spreadsheets output plugin for Embulk [![Build Status](https://travis-ci.org/kataring/embulk-output-google_spreadsheets.svg?branch=master)](https://travis-ci.org/kataring/embulk-output-google_spreadsheets) [![Gem Version](https://badge.fury.io/rb/embulk-output-google_spreadsheets.svg)](http://badge.fury.io/rb/embulk-output-google_spreadsheets)

Embulk output plugin to load into Google Spreadsheets.

## Overview

* **Plugin type**: output
* **Load all or nothing**: no
* **Resume supported**: no
* **Cleanup supported**: yes

## Configuration

- **email**: description (string, required)
- **p12file**: description (string, required)
- **key**: description (string, required)

## Example

```yaml
out:
  type: google_spreadsheets
  email: 'XXXXXXXXXXXXXXXXXXXXXXXX@developer.gserviceaccount.com'
  p12file: '/tmp/embulk.p12'
  key: '1RPXaB85DXM7sGlpFYIcpoD2GWFpktgh0jBHlF4m1a0A'
```


## Build

```
$ ./gradlew gem  # -t to watch change of files and rebuild continuously
```
