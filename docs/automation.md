# Automation

Other apps can turn ad blocking on and off by sending a broadcast to AdAway.

| Field | Value |
|---|---|
| Action | `org.adaway.action.SEND_COMMAND` |
| Package | `org.adaway` |
| Target | Broadcast receiver |
| Extra | `COMMAND` (string): `START` to enable ad blocking, `STOP` to disable it |

The receiver is protected by the `org.adaway.permission.SEND_COMMAND` permission ("Send commands to
AdAway"), with a `dangerous` protection level: the sending app must declare it in its manifest
(`<uses-permission android:name="org.adaway.permission.SEND_COMMAND" />`) and the user must grant it.
Automation apps that can't declare custom permissions can't send it.

For example, from a root shell (`su`), which is not subject to the permission check:

```sh
am broadcast -a org.adaway.action.SEND_COMMAND -p org.adaway --es COMMAND START
```

The command applies the current ad blocking method (root or VPN), exactly like the toggle in the app.
