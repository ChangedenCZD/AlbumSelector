package io.github.lijunguan.album.ui.fragment;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.konifar.fab_transformation.FabTransformation;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.github.lijunguan.album.AlbumConfig;
import io.github.lijunguan.album.ImgSelector;
import io.github.lijunguan.album.R;
import io.github.lijunguan.album.adapter.FolderListAdapter;
import io.github.lijunguan.album.adapter.ImageGridAdapter;
import io.github.lijunguan.album.base.BaseFragment;
import io.github.lijunguan.album.model.entity.AlbumFolder;
import io.github.lijunguan.album.model.entity.ImageInfo;
import io.github.lijunguan.album.presenter_view.AlbumContract;
import io.github.lijunguan.album.ui.activity.AlbumActivity;
import io.github.lijunguan.album.ui.widget.GridDividerDecorator;
import io.github.lijunguan.album.utils.ActivityUtils;
import io.github.lijunguan.album.utils.FileUtils;

import static io.github.lijunguan.album.utils.CommonUtils.checkNotNull;

/**
 * Created by lijunguan on 2016/4/21.
 * emial: lijunguan199210@gmail.com
 * blog: https://lijunguan.github.io
 */
public class AlbumFragment extends BaseFragment implements AlbumContract.View, View.OnClickListener {


    private AlbumContract.Presenter mPresenter;

    private AlbumConfig mAlbumConfig;
    /**
     * 展示图片浓缩图的Grid;
     */
    private RecyclerView mRvImageGrid;

    private ImageGridAdapter mImagesAdapter;
    /**
     * 相册目录列表
     */
    private RecyclerView mRvFolderList;

    private FolderListAdapter mFolderAdapter;
    /**
     * 相册目录列表弹出时的遮罩View
     */
    private View mOverlay;

    public FloatingActionButton mFab;

    private View mEmptyView;
    /**
     * 保存相机拍摄的照片
     */
    private File mTmpFile;


    public static AlbumFragment newInstance(@NonNull AlbumConfig config) {
        Bundle args = new Bundle();
        args.putParcelable(ImgSelector.ARG_ALBUM_CONFIG, config);
        AlbumFragment fragment = new AlbumFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mAlbumConfig = getArguments().getParcelable(ImgSelector.ARG_ALBUM_CONFIG);
        }
        //改用接口监听 而不是让Adapter持有Presenter对象，
        // 1.更符合MVP架构 2.解决当程序处于后台，系统因资源不足杀死App后，复原时会先执行Fragment的onCreate()方法
        //再执行 Activity的onCreate()方法，导致mPresenter throw NullPointerException异常
        mImagesAdapter = new ImageGridAdapter(mContext, mAlbumConfig, mItemListener);
        mFolderAdapter = new FolderListAdapter(mContext, mFolderItemClickListener);
    }

    /**
     * 图片Item点击事件监听
     */
    ImageItemListener mItemListener = new ImageItemListener() {

        @Override
        public void onImageClick(ImageInfo imageInfo) {
            mPresenter.previewImage(imageInfo);
        }

        @Override
        public void onSelectedImageClick(ImageInfo imageInfo, int maxCount, int position) {
            mPresenter.selectImage(imageInfo, maxCount, position);
        }

        @Override
        public void onUnSelectedImageClick(ImageInfo imageInfo) {
            mPresenter.unSelectImage(imageInfo);
        }

        @Override
        public void onCameraItemClick() {
            mPresenter.openCamera();
        }
    };

    FolderItemListener mFolderItemClickListener = new FolderItemListener() {
        @Override
        public void onFloderItemClick(AlbumFolder folder) {
            mPresenter.swtichFloder(folder);
        }
    };

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_album, container, false);
        initViews(rootView);
        return rootView;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mPresenter.start(); //初始化数据
    }

    private void initViews(View rootView) {
        mRvImageGrid = (RecyclerView) rootView.findViewById(R.id.rv_image_grid);
        mRvFolderList = (RecyclerView) rootView.findViewById(R.id.rv_album_list);
        mFab = (FloatingActionButton) rootView.findViewById(R.id.fab);
        mOverlay = rootView.findViewById(R.id.overlay);
        mEmptyView = rootView.findViewById(R.id.rl_no_image);

        initRecyclerView();

        mFab.setOnClickListener(this);
        mOverlay.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.fab) {
            showFolderList();
        } else if (id == R.id.overlay) {
            hideFolderList();
        }
    }

    private void initRecyclerView() {
        mRvFolderList.setHasFixedSize(true);
        mRvFolderList.setLayoutManager(new LinearLayoutManager(mContext));
        mRvFolderList.setAdapter(mFolderAdapter);

        mRvImageGrid.setHasFixedSize(true);
        //给RecclerView设置GridlayoutManager，并根据配置信息，指定列数
        mRvImageGrid.setLayoutManager(new GridLayoutManager(mContext, mAlbumConfig.getGridColumns()));
        mRvImageGrid.addItemDecoration(new GridDividerDecorator(mContext)); //添加divider
        mRvImageGrid.setAdapter(mImagesAdapter);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        mPresenter.result(requestCode, resultCode, mTmpFile);
    }


    @Override
    public void setPresenter(AlbumContract.Presenter presenter) {
        mPresenter = checkNotNull(presenter);
    }

    @Override
    public void showImages(List<ImageInfo> imageInfos) {
        mImagesAdapter.replaceData(imageInfos);
        mEmptyView.setVisibility(View.GONE);
    }

    @Override
    public void showFolderList() {
        FabTransformation.with(mFab)
                .setOverlay(mOverlay)
                .transformTo(mRvFolderList);

    }

    @Override
    public void hideFolderList() {
        FabTransformation.with(mFab)
                .setOverlay(mOverlay)
                .transformFrom(mRvFolderList);
    }

    @Override
    public void showSystemCamera() {
        //        // 跳转到系统照相机
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (cameraIntent.resolveActivity(mContext.getPackageManager()) != null) {
            // 设置系统相机拍照后的输出路径
            // 创建临时文件
            mTmpFile = null;
            try {
                mTmpFile = FileUtils.createTmpFile(mContext);
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (mTmpFile != null && mTmpFile.exists()) {
                cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(mTmpFile));
                startActivityForResult(cameraIntent, ImgSelector.REQUEST_OPEN_CAMERA);
            } else {
                showToast(getString(R.string.img_error));
            }
        } else {
            showToast(getString(R.string.msg_no_camera));
        }
    }

    @Override
    public void initFolderList(List<AlbumFolder> folders) {
        if (folders != null) {
            mFolderAdapter.setData(folders);
        }
    }

    @Override
    public void restoreChecbox(int position) {
        mImagesAdapter.notifyItemChanged(position);
    }

    @Override
    public void showSelectedCount(int count) {
        if (mContext instanceof AlbumActivity) {
            ((AlbumActivity) mContext).setSelectCount(count);
        }
    }

    @Override
    public void showImageDetailUi(ImageInfo imageInfo) {
        ImageDetailFragment fragment =
                (ImageDetailFragment) mContext.getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (fragment == null) {
            fragment = ImageDetailFragment.newInstance(imageInfo);
            ActivityUtils.addFragmentToActivity(mContext.getSupportFragmentManager(),
                    fragment,
                    R.id.fragment_container,
                    true);
        }
        mContext.getSupportFragmentManager().beginTransaction().hide(this).commit();
    }


    @Override
    public void selectComplete(List<String> imagePaths, boolean refreshMedia) {
        Intent data = new Intent();
        data.putStringArrayListExtra(ImgSelector.SELECTED_RESULT, (ArrayList<String>) imagePaths);
        // notify system ,保存拍照的照片到MediaStore,
        if (refreshMedia) {
            mContext.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(mTmpFile)));
        }
        mContext.setResult(Activity.RESULT_OK, data);
        mContext.finish();
    }


    @Override
    public void showEmptyView() {
        mEmptyView.setVisibility(View.VISIBLE);
    }


    @Override
    public void showToast(CharSequence message) {
        Snackbar.make(getView(), message, Snackbar.LENGTH_SHORT).show();
    }


    public interface ImageItemListener {

        void onImageClick(ImageInfo imageInfo);

        void onSelectedImageClick(ImageInfo imageInfo, int maxCount, int position);

        void onUnSelectedImageClick(ImageInfo imageInfo);

        void onCameraItemClick();
    }

    public interface FolderItemListener {
        void onFloderItemClick(AlbumFolder folder);
    }
}
