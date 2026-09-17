package com.fluidreader.app

import android.app.Application

/** Application entry point. No global singletons are needed yet - each screen owns its own
 * small stores/pipelines - but this class is kept as the natural place to add them later. */
class FluidReaderApp : Application()
