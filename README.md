# Shandor

Gives emails a shelf life and deletes them when they're expired.

Relies on [notmuch](http://notmuchmail.org).

Based on the same thoughts as [Moirai](https://github.com/rmoehn/moirai).

Right now the code is pretty horrible and usability is near zero. And I'm not
sure whether one day it will delete all my emails. Good that I have backups.
Anyway, I think shelflives are pretty awesome.

See [The Web of Alexandria](http://worrydream.com/TheWebOfAlexandria/) and [The
Web of Alexandria (follow-up)](http://worrydream.com/TheWebOfAlexandria/2.html).

Also contains a half-arsed wrapper for accessing the notmuch library from
Clojure as well as an undocumented function to make the use of
[clojure-jna](https://github.com/Chouser/clojure-jna) more idiomatic. If you're
wanting to do useful Clojure programming, but don't know what to do, feel free
to take these and make them better and publish/contribute them.

## Copyright and License

The MIT License (MIT)

Copyright (c) 2015 Richard Möhn

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
