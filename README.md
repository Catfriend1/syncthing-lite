This is an experiment to revive the Syncthing-Lite App. Let's discuss it on [Syncthing-Forum](https://forum.syncthing.net/).

# Syncthing Lite for Android

[![MPLv2 License](https://img.shields.io/badge/license-MPLv2-blue.svg?style=flat-square)](https://www.mozilla.org/MPL/2.0/)

This project is an Android app, that works as a client for a [Syncthing][1] share (accessing 
Syncthing devices in the same way a client-server file sharing app accesses its proprietary server). 

This is a client-oriented implementation, designed to work online by downloading and 
uploading files from an active device on the network (instead of synchronizing a local copy of 
the entire repository).
Due to that, you will see a sync progress of 0% at other devices (and this is expected).
This is quite different from the way the [syncthing-android][2] works, 
and it's useful for those devices that cannot or do not wish to download the entire repository (for 
example, mobile devices with limited storage available, wishing to access a syncthing share).

This project is based on syncthing-java (which is in this repository too), a java implementation of Syncthing protocols.

Due to the behaviour of this App and the [behaviour of the Syncthing Server](https://github.com/syncthing/syncthing/issues/5224),
you can't reconnect for some minutes if the App was killed (due to removing from the recent App list) or the connection was interrupted.
This does not apply to local discovery connections.

## Translations

The project is currently not translated on Weblate, but may be in the future.

## Aknowledgements

This project was forked from [GitHub/syncthing-lite](https://github.com/syncthing/syncthing-lite).

Special thanks to the former maintainers:

- [l-jonas](https://github.com/l-jonas)
- [nutomic](https://github.com/nutomic)
- [davide-imbriaco](https://github.com/davide-imbriaco)

## License
All code is licensed under the [MPLv2 License][4].

[1]: https://syncthing.net/
[2]: https://github.com/syncthing/syncthing-android
[3]: https://developer.android.com/studio/index.html
[4]: LICENSE
