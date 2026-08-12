# Security Policy

## Reporting a vulnerability

Please report suspected vulnerabilities privately rather than in a public
issue. Use GitHub's [private vulnerability reporting](https://github.com/martin-k-m/unscroll/security/advisories/new)
for this repository, or email martinkmuskov@gmail.com.

Include your device model, Android version, and the steps that reproduce the
problem. You can expect an acknowledgement within a few days.

## Design and data

unscroll is built to have almost no security surface, on purpose:

- **No network.** The app declares no internet permission, makes no network
  calls, and contains no analytics or crash reporting. Nothing leaves the phone.
- **No accounts.** There is no sign-in and no server, so there are no
  credentials to compromise.
- **Local state only.** Time budgets and settings are stored on-device.

The one sensitive capability is the **accessibility service**, which observes
window changes to detect a short-video feed. It reads what it needs to match a
feed and cover it, and stores none of it. Reports that this service can be made
to leak observed screen contents, persist them, or transmit them anywhere are
the most valuable, since that would break the app's central promise.
