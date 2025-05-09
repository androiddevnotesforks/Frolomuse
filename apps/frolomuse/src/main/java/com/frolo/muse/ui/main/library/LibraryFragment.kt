package com.frolo.muse.ui.main.library

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.viewpager.widget.ViewPager
import com.frolo.core.ui.fragment.WithCustomStatusBar
import com.frolo.core.ui.marker.ScrolledToTop
import com.frolo.debug.DebugUtils
import com.frolo.muse.R
import com.frolo.muse.databinding.FragmentLibraryBinding
import com.frolo.muse.model.Library
import com.frolo.muse.repository.Preferences
import com.frolo.muse.ui.base.BaseFragment
import com.frolo.muse.ui.base.FragmentContentInsetsListener
import com.frolo.muse.ui.base.OnBackPressedHandler
import com.frolo.muse.util.CollectionUtil
import com.frolo.ui.FragmentUtils
import com.frolo.ui.StyleUtils


class LibraryFragment: BaseFragment(),
    OnBackPressedHandler,
    FragmentContentInsetsListener,
    ScrolledToTop,
    WithCustomStatusBar {
        
    private var _binding: FragmentLibraryBinding? = null
    private val binding: FragmentLibraryBinding get() = _binding!!

    private val preferences: Preferences by prefs()

    private val viewModel by viewModel<LibraryViewModel>()

    /**
     * The sections that are currently being displayed. Used for comparison with the sections
     * stored in the preferences. These sections need to be kept in the memory
     * and not just rely on the saved instance state, because the fragment view can be created
     * on the same fragment instance without providing the saved state.
     */
    private var currentSections: List<@Library.Section Int>? = null

    private val onPageChangeCallback = object : ViewPager.OnPageChangeListener {
        override fun onPageScrollStateChanged(state: Int) = Unit

        override fun onPageScrolled(
            position: Int,
            positionOffset: Float,
            positionOffsetPixels: Int
        ) {
            // it's better to check only if position offset is in range -0,1..0,1
            if (positionOffset > -0.1 && positionOffset < 0.1) {
                invalidateFab()
            }
        }

        override fun onPageSelected(position: Int) {
            invalidateFab()
        }
    }

    override val statusBarColor: Int get() = statusBarColorRaw
    override val statusBarColorRaw: Int get() {
        val uiContext = view?.context ?: kotlin.run {
            DebugUtils.dumpOnMainThread(IllegalStateException("Fragment not attached"))
            return Color.TRANSPARENT
        }
        return StyleUtils.resolveColor(uiContext, com.google.android.material.R.attr.colorSurface)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentLibraryBinding.inflate(inflater)
        return _binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.tbActions.setTitle(R.string.nav_library)
        (activity as? AppCompatActivity)?.setSupportActionBar(binding.tbActions)

        if (savedInstanceState != null && savedInstanceState.containsKey(STATE_KEY_SECTIONS)) {
            // Restoring the sections
            currentSections = savedInstanceState.getIntegerArrayList(STATE_KEY_SECTIONS)
        }

        // Retrieving library sections from the preferences should be a synchronous operation
        val actualSections = preferences.librarySections.filter { section ->
            preferences.isLibrarySectionEnabled(section)
        }

        if (!areSectionListsEqual(currentSections, actualSections)) {
            currentSections = actualSections
            // It's a compelled workaround to prevent the action bar from adding menus
            // of the previous fragments that are not at the current position
            FragmentUtils.removeAllFragmentsNow(childFragmentManager)
        }

//        view.fitsSystemWindows = true
//        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
//            binding.tbActions.updatePadding(top = insets.systemWindowInsetTop)
//            WindowInsetsCompat.CONSUMED
//        }

        binding.vpSections.apply {
            adapter = LibraryPageAdapter(context, childFragmentManager, actualSections)
            // Registering OnPageChangeListener should go after the adapter is set up
            addOnPageChangeListener(onPageChangeCallback)
        }

        binding.tlSections.setupWithViewPager(binding.vpSections)

        binding.fabAction.setOnClickListener {
            dispatchClickOnFab()
        }

        invalidateFab()
    }

    private fun areSectionListsEqual(
        sections1: List<@Library.Section Int>?,
        sections2: List<@Library.Section Int>?
    ): Boolean {
        if (sections1 == null || sections2 == null) {
            return false
        }
        return CollectionUtil.areListContentsEqual(sections1, sections2)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        observeViewModel(viewLifecycleOwner)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        currentSections?.also { safeSections ->
            outState.putIntegerArrayList(STATE_KEY_SECTIONS, ArrayList<Int>(safeSections))
        }
    }

    override fun onDestroyView() {
        binding.vpSections.removeOnPageChangeListener(onPageChangeCallback)
        super.onDestroyView()
        _binding = null
    }

    override fun handleOnBackPressed(): Boolean {
        val position = binding.vpSections.currentItem
        val adapter = binding.vpSections.adapter as? LibraryPageAdapter ?: return false
        val page = adapter.getPageAt(position)
        if (page is OnBackPressedHandler && FragmentUtils.isInForeground(page)) {
            return page.handleOnBackPressed()
        }
        return false
    }

    private fun peekCurrentPage(): Fragment? {
        view ?: return null
        val adapter = binding.vpSections.adapter as? LibraryPageAdapter
        return adapter?.getPageAt(binding.vpSections.currentItem)
    }

    private fun invalidateFab() {
        val currFragment = peekCurrentPage()
        if (currFragment is ActionButtonCallback && currFragment.requiresActionButton()) {
            currFragment.onDecorateActionButton(binding.fabAction)
            binding.fabAction.show()
        } else {
            binding.fabAction.hide()
        }
    }

    private fun dispatchClickOnFab() {
        val currFragment = peekCurrentPage()
        if (currFragment is ActionButtonCallback && currFragment.requiresActionButton()) {
            currFragment.onHandleActionButtonClick()
        }
    }

    override fun applyContentInsets(left: Int, top: Int, right: Int, bottom: Int) {
        binding.fabAction.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            leftMargin += left
            topMargin += top
            rightMargin += right
            bottomMargin += bottom
        }
    }

    private fun observeViewModel(owner: LifecycleOwner) = with(viewModel) {
//        bannerConfig.observe(owner) { config ->
//            if (config != null) {
//                ad_view_container.loadBanner(config.placementId)
//            } else {
//                ad_view_container.clearBanner()
//            }
//        }
    }

    override fun scrollToTop() {
        binding.appBarLayout.setExpanded(true, true)
        peekCurrentPage()?.also { page ->
            if (page is ScrolledToTop && FragmentUtils.isInForeground(page)) {
                page.scrollToTop()
            }
        }
    }

    companion object {

        private const val LOG_TAG = "LibraryFragment"

        private const val STATE_KEY_SECTIONS = "library_sections"

        // Factory
        fun newInstance() = LibraryFragment()

    }
}