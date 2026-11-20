---
icon: lucide/images
---

# Gallery and viewer

The gallery is the grid of your photos and videos; the viewer is one photo or video full screen. Both are black, take the whole screen and turn with the phone when auto-rotate is on (the camera and the editor stay upright).

## Opening them

- **The camera's thumbnail** opens the viewer on the photo that is still processing, otherwise on the newest photo or video. The picture grows out of the thumbnail and shrinks back into it when you return. With nothing to show, it opens the grid.
- **Swipe down in the viewer** for the grid, **swipe up** for the camera. If a stack is still processing, Lilac asks before it opens the camera (see [Exposure bracketing](hdr.md#when-it-is-off)).
- **Optional second icon:** Settings › Setup › Interface has a switch (off by default) that adds a **Lilac Gallery** launcher icon. It opens straight to the grid without starting the camera.

## The grid

One line floats over the grid: back, the **Lilac** and **System** tabs, and the render queue (with a count, or a mark when something failed). Photos fade out under it.

- **Lilac** holds what Lilac made or took in. **System** shows other apps' photos and videos: they are only viewed, and stay in the phone's gallery.
- **Swipe sideways** to go to the other tab. Each tab keeps its scroll place.
- **Tags** at the bottom left of a tile, at most two, in the viewer's words: **RAW**, **×N** (versions), **ME ×N** (multiple exposure), **BURST n**, **LIVE**, the video's length, **120 FPS** or **960 FPS**, **IMPORT**, **LILAC** (on the System tab), a lock for a protected photo, and **FAILED** in red.
- **A tile's edge shows processing**, as the thumbnail's does: an arc runs while it renders, dots while it waits, and the picture is dimmed and slightly blurred while it is busy. See [the render queue](pro-mode.md#the-render-queue).
- **The date pill** and a slim bar show while you scroll fast. A landscape grid has five columns.

### Selecting

Long-press a photo, then tap more. A photo that is still processing cannot be selected; a long press says why. A floating row takes the action: **Export**, **Share**, **Paste look**, **Delete** and **Re-render**. An action that cannot apply is dimmed and says why.

On the System tab the first action is **Add to Lilac**: it copies the photos into Lilac's library, skipping any already there. A copy can be rendered again, kept as versions, rated and protected; a system photo can only be viewed.

### Delete has an Undo

A deleted photo disappears at once and an **Undo** banner shows for 5 seconds. The files go after that, when you leave the viewer or Lilac goes to the background. A sheet appears only when there is a choice: this version or all, this frame or the whole burst, or exported copies. Protected photos are skipped. Another app's photo goes to the system trash, and Undo asks Android to restore it.

## The viewer

Three rows sit over the picture, with no background. Tap the picture to hide them.

- **Top row:** the grid icon, a short count while you page, the rating and the photo's tags. A tag explains itself when tapped; **LIVE** plays, **BURST** opens the burst and **HDR** opens its strength.
- **Item row:** the version strip, the burst strip or the video timeline, and the processing line.
- **Action row:** **Camera**, **Share**, **Edit**, **Delete**, **More**.

Double-tap zooms to one pixel of the preview per screen pixel, at the tapped point. In landscape the rows move to the edges: the actions as a column on the right, the top row on the left, and the picture stays centred.

### Versions, bursts and processing

- **Versions:** a Pro photo and its re-rendered copies form one tile. The strip switches between them; the original reads **ORIG**. The edge of a version's tile shows its state.
- **Bursts:** the strip shows the burst's frames and the cover has a star. **More** offers **Use as cover**, **Export** of the shown frame and **Export all frames**. **Edit** works on the cover.
- **Processing line:** a ring or an icon, the stage, one action and a link to the queue: **Pause** while rendering, **Resume** when paused, **Retry** when it failed, **Render** for a skipped photo and **Render now** for one that waits. **Render now** puts it next, after the render already running.

### Rating and protecting

The star button adds a star per tap and clears after five. **Protect** is a switch in More: it shows a lock tag and refuses deleting the photo in the viewer and in the grid. Both belong to the photo, on every version of a stack.

### More

**More** opens a sheet with rows in three groups: **This photo**, **Look** and **Export**. It also holds **Info**, **Shooting info** (the exposure line and a histogram above the item row), **HDR display** and **Processing queue**. A row that cannot apply says why. HDR display is hidden on a screen with no HDR headroom, since it would look the same.

## Videos

A video fills the screen and has Lilac's own controls instead of the system player's. The timeline sits in the item row, play and pause in the middle of the picture, and the **¼× / 1×** choice and sound above the timeline. A tap shows or hides the rows.

A [120 fps clip](video.md#frame-rates) shows its slowed part as a band on the timeline. Drag its ends; it plays at ¼× inside and 1× outside, and the range is kept with the clip.

While a clip is paused, **previous frame**, **Save frame** and **next frame** can appear. Save frame imports the frame as a new photo with the clip's look. They are off by default: turn on **Show on the picture** in More. Frame step shows only after the clip has played and been paused.

A clip with no look plays straight to the screen. A clip with a look larger than 1080p is scaled down to 1080p before the look is applied, because 4K through the look made this phone's graphics chip fail. A clip that cannot play says so.

## The editor

**Edit** opens the [editor](editor.md). The picture glides from its place in the viewer to its place in the editor, and back, unless you are zoomed in or in landscape.
