"""Optional system-tray front end (Windows/macOS/Linux), built on `pystray`.

pystray (and its backing toolkit, e.g. Xlib on Linux) is imported lazily,
inside run_tray(), never at module import time: this sandbox and any other
headless environment can still import and test every other module in this
package even though a tray cannot actually be shown here. Install the `tray`
extra (`pip install nua-sovereign-client[tray]`) to use this module.
"""
from __future__ import annotations

from .api_client import ApiClient
from .identity import ClientIdentity
from .pairing_client import PairingClient


def run_tray(base_url: str) -> None:
    import pystray
    from PIL import Image, ImageDraw

    identity = ClientIdentity()
    api = ApiClient(base_url, identity)

    def _status_icon_image() -> Image.Image:
        image = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        draw = ImageDraw.Draw(image)
        try:
            api.health()
            color = (40, 180, 90, 255)
        except Exception:
            color = (200, 60, 60, 255)
        draw.ellipse((8, 8, 56, 56), fill=color)
        return image

    def _on_pair(icon: "pystray.Icon", item: "pystray.MenuItem") -> None:
        pairing = PairingClient(base_url, identity)
        result = pairing.request_pairing()
        icon.notify(
            f"Pairing request sent (challenge {result.challenge_id[:8]}...). "
            "Approve it on the companion node to finish pairing."
        )

    def _on_quit(icon: "pystray.Icon", item: "pystray.MenuItem") -> None:
        icon.stop()

    icon = pystray.Icon(
        "nua-sovereign-client",
        icon=_status_icon_image(),
        title="NUA Sovereign Client",
        menu=pystray.Menu(
            pystray.MenuItem("Pair with companion", _on_pair),
            pystray.MenuItem("Quit", _on_quit),
        ),
    )
    icon.run()
