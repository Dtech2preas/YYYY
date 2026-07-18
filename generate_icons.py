from PIL import Image
import os

# Create directories
sizes = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192
}

base_dir = "android_app/src/main/res"

img = Image.open("logo.png")

for mipmap, size in sizes.items():
    folder = os.path.join(base_dir, mipmap)
    os.makedirs(folder, exist_ok=True)

    resized = img.resize((size, size), Image.Resampling.LANCZOS)
    resized.save(os.path.join(folder, "ic_launcher.png"))

    # Also save as round for simplicity, even though proper round icons usually have a mask
    resized.save(os.path.join(folder, "ic_launcher_round.png"))

print("Icons generated.")
