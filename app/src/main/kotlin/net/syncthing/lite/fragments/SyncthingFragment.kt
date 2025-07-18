package net.syncthing.lite.fragments

import net.syncthing.lite.activities.SyncthingActivity
import net.syncthing.lite.async.CoroutineFragment
import net.syncthing.lite.library.LibraryHandler

abstract class SyncthingFragment : CoroutineFragment() {
    val libraryHandler: LibraryHandler by lazy { 
        // Use parent activity's LibraryHandler if it's a SyncthingActivity, otherwise create our own
        (activity as? SyncthingActivity)?.libraryHandler ?: LibraryHandler(context = requireContext())
    }

    override fun onStart() {
        super.onStart()

        // Only start LibraryHandler if we created our own (not using parent's)
        if (activity !is SyncthingActivity) {
            libraryHandler.start {
                // TODO: check if this is still useful
                onLibraryLoaded()
            }
        } else {
            // If using parent's LibraryHandler, just call onLibraryLoaded
            onLibraryLoaded()
        }
    }

    override fun onStop() {
        super.onStop()

        // Only stop LibraryHandler if we created our own (not using parent's)
        if (activity !is SyncthingActivity) {
            libraryHandler.stop()
        }
    }

    open fun onLibraryLoaded() {}
}
