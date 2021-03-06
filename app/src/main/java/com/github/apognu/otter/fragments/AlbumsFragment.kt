package com.github.apognu.otter.fragments

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import androidx.transition.Fade
import androidx.transition.Slide
import com.github.apognu.otter.R
import com.github.apognu.otter.activities.MainActivity
import com.github.apognu.otter.adapters.AlbumsAdapter
import com.github.apognu.otter.repositories.AlbumsRepository
import com.github.apognu.otter.repositories.ArtistTracksRepository
import com.github.apognu.otter.repositories.Repository
import com.github.apognu.otter.utils.*
import com.squareup.picasso.Picasso
import jp.wasabeef.picasso.transformations.RoundedCornersTransformation
import kotlinx.android.synthetic.main.fragment_albums.*
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AlbumsFragment : OtterFragment<Album, AlbumsAdapter>() {
  override val viewRes = R.layout.fragment_albums
  override val recycler: RecyclerView get() = albums
  override val alwaysRefresh = false

  private lateinit var artistTracksRepository: ArtistTracksRepository

  var artistId = 0
  var artistName = ""
  var artistArt = ""

  companion object {
    fun new(artist: Artist, _art: String? = null): AlbumsFragment {
      val art = _art ?: if (artist.albums?.isNotEmpty() == true) artist.albums[0].cover.original else ""

      return AlbumsFragment().apply {
        arguments = bundleOf(
          "artistId" to artist.id,
          "artistName" to artist.name,
          "artistArt" to art
        )
      }
    }

    fun openTracks(context: Context?, album: Album?, fragment: Fragment? = null) {
      if (album == null) {
        return
      }

      (context as? MainActivity)?.let {
        fragment?.let { fragment ->
          fragment.onViewPager {
            exitTransition = Fade().apply {
              duration = AppContext.TRANSITION_DURATION
              interpolator = AccelerateDecelerateInterpolator()

              view?.let {
                addTarget(it)
              }
            }
          }
        }
      }

      (context as? AppCompatActivity)?.let { activity ->
        val nextFragment = TracksFragment.new(album).apply {
          enterTransition = Slide().apply {
            duration = AppContext.TRANSITION_DURATION
            interpolator = AccelerateDecelerateInterpolator()
          }
        }

        activity.supportFragmentManager
          .beginTransaction()
          .replace(R.id.container, nextFragment)
          .addToBackStack(null)
          .commit()
      }
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    arguments?.apply {
      artistId = getInt("artistId")
      artistName = getString("artistName") ?: ""
      artistArt = getString("artistArt") ?: ""
    }

    adapter = AlbumsAdapter(context, OnAlbumClickListener())
    repository = AlbumsRepository(context, artistId)
    artistTracksRepository = ArtistTracksRepository(context, artistId)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    cover?.let { cover ->
      Picasso.get()
        .maybeLoad(maybeNormalizeUrl(artistArt))
        .noFade()
        .fit()
        .centerCrop()
        .transform(RoundedCornersTransformation(16, 0))
        .into(cover)
    }

    artist.text = artistName

    play.setOnClickListener {
      val loader = CircularProgressDrawable(requireContext()).apply {
        setColorSchemeColors(ContextCompat.getColor(requireContext(), android.R.color.white))
        strokeWidth = 4f
      }

      loader.start()

      play.icon = loader
      play.isClickable = false

      lifecycleScope.launch(IO) {
        artistTracksRepository.fetch(Repository.Origin.Network.origin)
          .map { it.data }
          .toList()
          .flatten()
          .shuffled()
          .also {
            CommandBus.send(Command.ReplaceQueue(it))

            withContext(Main) {
              play.icon = requireContext().getDrawable(R.drawable.play)
              play.isClickable = true
            }
          }
      }
    }
  }

  override fun onResume() {
    super.onResume()

    var coverHeight: Float? = null

    scroller.setOnScrollChangeListener { _: View?, _: Int, scrollY: Int, _: Int, _: Int ->
      if (coverHeight == null) {
        coverHeight = cover.measuredHeight.toFloat()
      }

      cover.translationY = (scrollY / 2).toFloat()

      coverHeight?.let { height ->
        cover.alpha = (height - scrollY.toFloat()) / height
      }
    }
  }

  inner class OnAlbumClickListener : AlbumsAdapter.OnAlbumClickListener {
    override fun onClick(view: View?, album: Album) {
      openTracks(context, album, fragment = this@AlbumsFragment)
    }
  }
}