package mk.tv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import mk.tv.data.Http

class MkTvApp : Application(), ImageLoaderFactory {
    /** Logos use the shared client: some hosts (e.g. Wikimedia) reject requests without a browser User-Agent. */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient {
            Http.client.newBuilder()
                .addInterceptor { chain ->
                    chain.proceed(chain.request().newBuilder().header("User-Agent", Http.USER_AGENT).build())
                }
                .build()
        }
        .crossfade(true)
        .build()
}
