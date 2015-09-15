# Shandor

[<img alt="Sandor Katz Monticello 12 Sep 2015.jpg"
      src="https://upload.wikimedia.org/wikipedia/commons/f/ff/Sandor_Katz_Monticello_12_Sep_2015.jpg"
      height="240"
      width="177">](https://commons.wikimedia.org/wiki/File:Sandor_Katz_Monticello_12_Sep_2015.jpg#/media/File:Sandor_Katz_Monticello_12_Sep_2015.jpg)

Gives emails a shelf life and deletes them when they're expired.


## Summary

My email archive has three main categories of emails:

 - Emails that I read once and won't need ever again.

 - Emails that are only relevant for a short period of time (up to a year) and
   not needed after that.

 - Emails that have value (to me) for a long time (more than a year).

Shandor takes charge of the first two categories by actually deleting those
emails at the right time.

For that I tag my emails using [notmuch](http://notmuchmail.org). Every day
Shandor looks into the notmuch database and figures out what to do with each
email based on its tags. Emails with the tag `deleted` are actually deleted after
two weeks. Emails with the tag `1w` or `6m` are going to be tagged as `deleted`
after one week or six months, respectively.


## Usage

**WARNING** This program deletes files. *Deletes* them. It is supposed to do so
in an orderly fashion, but I don't give any guarantees that it might not delete
your whole email folder. Read the code and make backups before you run it.

**Download and compile** the code.

```shell
$ git clone https://github.com/rmoehn/shandor
$ cd shandor
$ lein uberjar
```

Then, **every day**, run:

```shell
$ java -jar target/shandor-<current version>.jar <path to directory with notmuch DB>
$ notmuch new
```

Shandor will do the following:

 - Emails with tag **`deleted`**: mark them for removal in two weeks.

 - Emails with a **time-to-live**, i.e. tag `Nd`, `Nw`, `Nm` or `Ny`, where `N`
   is a natural number: mark them for expiry in `N` days, weeks, months or
   years.

 - Emails that are **expired**: mark them for removal in two weeks.

 - Emails whose **removal date** lies in the **past**: actually delete them.

Right now, Shandor only deletes the **email files** from disk and not the
corresponding entries in the **database**. `notmuch new` takes care of that.


## Comments

Based on the same thoughts as [Moirai](https://github.com/rmoehn/moirai).

Right now the code is pretty horrible and there are no tests. And I'm not sure
whether one day it will delete all my emails. Good that I have backups. Anyway,
I think shelf lives are pretty awesome.

See also [The Web of Alexandria](http://worrydream.com/TheWebOfAlexandria/) and
[The Web of Alexandria
(follow-up)](http://worrydream.com/TheWebOfAlexandria/2.html).

Contains a half-arsed wrapper for accessing the notmuch library from Clojure, as
well as an undocumented function to make the use of
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
