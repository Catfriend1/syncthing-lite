# Syncthing-Lite for Android

[![MPLv2 License](https://img.shields.io/badge/license-MPLv2-blue.svg?style=flat-square)](https://www.mozilla.org/MPL/2.0/)<a href="https://tooomm.github.io/github-release-stats/?username=Catfriend1&repository=syncthing-lite" alt="GitHub Stats"><img src="https://img.shields.io/github/downloads/Catfriend1/syncthing-lite/total.svg" /></a>[![Build App](https://github.com/Catfriend1/syncthing-lite/actions/workflows/build-app.yaml/badge.svg)](https://github.com/Catfriend1/syncthing-lite/actions/workflows/build-app.yaml)

⚡ Revival of the app which was abandoned years ago. Let's discuss it on [Syncthing-Forum](https://forum.syncthing.net/).

✅ Safety warning: Please always ensure that you have backed up your data by other means and that this backup is up to date before using this app. The app is still in early development.

⚠️ We assume no liability for data corruption or loss, although we make every effort to deliver quality.

This is an Android app which you can use as a client to access [Syncthing][1] shares offered by remote devices. It works similar to file sharing apps  accessing their server. 

This is a client-oriented implementation, designed to work online by downloading and uploading files from an active device on the network instead of synchronizing a local copy of the entire folder. Due to that, you will see a sync progress of 0% at other devices which is expected. This is useful for devices where you don't want to download the entire contents of a shared folder. For example, mobile devices with limited storage available where you like to access a syncthing shared folder. This is quite different from the way the [Syncthing-Fork][2] app works.

Please note that for technical reasons you can't reconnect via relay connection for some minutes after the app was closed. This may happen due to removing from the recent apps list, force close or an interrupted connection to remote device. More info about Syncthing's behaviour can be found on this [ticket](https://github.com/syncthing/syncthing/issues/5224).

This constraint does not apply for connections directly established through the local network.

## Translations

The project is currently not translated on [Weblate](https://hosted.weblate.org/projects/syncthing/#components), but may be in the future.

## Aknowledgements

This project was forked from [GitHub/syncthing-lite](https://github.com/syncthing/syncthing-lite).

Special thanks to the former maintainers:

- [l-jonas](https://github.com/l-jonas)
- [nutomic](https://github.com/nutomic)
- [davide-imbriaco](https://github.com/davide-imbriaco)

## License
All code is licensed under the [MPLv2 License][3].

[1]: https://syncthing.net/
[2]: https://github.com/Catfriend1/syncthing-android
[3]: LICENSE
