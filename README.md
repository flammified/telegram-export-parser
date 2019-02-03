# Telegram Export Parser

A parser for telegram chat exports.

Current features:

- Parsing a directory of logs
- Extracting text messages
- Extracting datetimes
- Removing HTML in messages or extracting text from hyperlinks
- Outputting to sql

### Usage

```
Parser for telegram HTML logs

Usage: ./parser <directory> <dbfilename> <options>
E.g. ./parser logs messages.db

Options:
  -u, --filter-html        Remove html blocks. Defaults to false.
  -e, --extract-link-text  Extract text from hyperlinks. Defaults to false.
```
