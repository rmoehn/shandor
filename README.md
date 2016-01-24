# Shandor

[![Sandor Katz Monticello 12 Sep 2015.jpg](https://upload.wikimedia.org/wikipedia/commons/thumb/f/ff/Sandor_Katz_Monticello_12_Sep_2015.jpg/177px-Sandor_Katz_Monticello_12_Sep_2015.jpg)](https://commons.wikimedia.org/wiki/File:Sandor_Katz_Monticello_12_Sep_2015.jpg#/media/File:Sandor_Katz_Monticello_12_Sep_2015.jpg)

Gives emails a shelf life and deletes them when they're expired.


## Summary

My emails fall into several categories:

 - Emails that I read once and won't need ever again.

 - Emails that are only relevant for a short period of time (up to a year) and
   not needed after that.

 - Emails that have value (to me) for a long time. For those I usually want to
   decide after some time whether they're still valuable.

Shandor helps me by deleting the emails from the first two categories at the
right time. It also reminds me when it's time to judge those long-term emails
again.

For that I tag my emails using [notmuch](http://notmuchmail.org). Every time I
call Shandor, it looks into the notmuch database and figures out what to do with
each email based on its tags. Emails with the tag `deleted` are actually deleted
after two weeks. Emails with the tag `1w` or `6m` are going to be tagged as
`deleted` after one week or six months, respectively. Emails with the tag `j1y`
or `j5y` will be tagged `judge` after one or five years, respectively.

I also have categories whose emails should have a certain lifespan. For example,
emails from Amazon can be deleted after three years. Since I don't want to put
the tag `3y` on every Amazon email, I can specify a premap that maps any tag to
a Shandor tag.


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
$ java -jar target/shandor-<current version>.jar \
    <path to directory with notmuch DB>
    [<path to the premap>]
$ notmuch new
```

Shandor will do the following:

 - Emails with tag **`deleted`**: mark them for removal in two weeks.

 - Emails with a **time-to-live**, i.e. tag `Nd`, `Nw`, `Nm` or `Ny`, where `N`
   is a natural number: mark them for expiry in `N` days, weeks, months or
   years.

 - Emails that are **expired**: mark them for removal in two weeks.

 - Emails whose **removal date** lies in the **past**: actually delete them.

 - Emails with a **time-to-judge**, i.e. tag `jNd`, `jNw`, `jNm` or `jNy`, where
   `N` is a natural number: mark them for judgement in `N` days, weeks, months
   or years.

 - Emails whose **judgement date has passed**: tag them with `judge`.

Right now, Shandor only deletes the **email files** from disk and not the
corresponding entries in the **database**. `notmuch new` takes care of that.

The **premap** is an EDN file with a map from general tags to Shandor tags, like
this:

```clojure
{"amazon" "3y"
 "x-newsletter" "j5m"}
```

Providing it replaces the tags on the left side with the tags on the right side
in Shandor's eyes. In other words, Shandor's compost algorithm **won't see**
`amazon` or `x-newsletter` on an email, but `3y` or `j5m` respectively.

Shandor **logs** extensively, so that you can reconstruct what happened to your
emails. If you don't want logs, redirect Shandor's STDOUT to `/dev/null`. I
recommend appending it to a log file, though. And I recommend using `logrotate`
on that file.

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


## Known problems

If you get an error that contains »There is an incompatible JNA native library
installed on this system«, uninstalling your system's libjna might help. Of
course you can't do this if something depends on a system-wide installation of
libjna (as opposed to an installation in your local Maven repo).


## Copyright and License

See `LICENSE.txt` in this repo.
