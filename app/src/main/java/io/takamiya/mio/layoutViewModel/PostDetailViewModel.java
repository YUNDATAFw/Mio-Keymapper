package io.takamiya.mio.layoutViewModel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class PostDetailViewModel extends ViewModel {
    private MutableLiveData<String> authorName = new MutableLiveData<>();
    private MutableLiveData<String> authorNickname = new MutableLiveData<>();
    private MutableLiveData<String> sectionName = new MutableLiveData<>();
    private MutableLiveData<String> title = new MutableLiveData<>();
    private MutableLiveData<String> content = new MutableLiveData<>();
    private MutableLiveData<String> createdAt = new MutableLiveData<>();
    private MutableLiveData<String> authorAvatar = new MutableLiveData<>();
    private MutableLiveData<String> authorUserLevelTitle = new MutableLiveData<>();
    private MutableLiveData<Integer> views = new MutableLiveData<>();
    private MutableLiveData<Integer> like = new MutableLiveData<>();
    private MutableLiveData<Integer> favorites = new MutableLiveData<>();
    private MutableLiveData<Integer> coins = new MutableLiveData<>();
    private MutableLiveData<Integer> auditStatus = new MutableLiveData<>();
    private MutableLiveData<Integer> authorMemberLevel = new MutableLiveData<>();
    private MutableLiveData<Boolean> isRecommend = new MutableLiveData<>();
    private MutableLiveData<Boolean> isTop = new MutableLiveData<>();

    public void loadData(String authorName,String authorNickname, String sectionName, String title, String content, String createdAt, int views, int like, int favorites, int coins, int auditStatus, boolean isRecommend, boolean isTop, String authorAvatar, String authorUserLevelTitle, int authorMemberLevel) {
        this.authorName.setValue(authorName);
        this.authorNickname.setValue(authorNickname);
        this.sectionName.setValue(sectionName);
        this.title.setValue(title);
        this.content.setValue(content);
        this.createdAt.setValue(createdAt);
        this.views.setValue(views);
        this.like.setValue(like);
        this.favorites.setValue(favorites);
        this.coins.setValue(coins);
        this.auditStatus.setValue(auditStatus);
        this.isTop.setValue(isTop);
        this.isRecommend.setValue(isRecommend);
        this.authorAvatar.setValue(authorAvatar);
        this.authorUserLevelTitle.setValue(authorUserLevelTitle);
        this.authorMemberLevel.setValue(authorMemberLevel);
    }

    private LiveData<String> getAuthorName() {
        return authorName;
    }
    public LiveData<String> getAuthorNickname() {
        return authorNickname;
    }
    public LiveData<String> getSectionName() {
        return sectionName;
    }

    public LiveData<String> getTitle() {
        return title;
    }

    public LiveData<String> getCreatedAt() {
        return createdAt;
    }

    public LiveData<Integer> getViews() {
        return views;
    }

    public LiveData<Integer> getLike() {
        return like;
    }

    public LiveData<Integer> getFavorites() {
        return favorites;
    }

    public LiveData<Integer> getCoins() {
        return coins;
    }

    public void addToLike(int delta) {
        Integer value = like.getValue();
        if (value != null) like.setValue(value + delta);
    }

    public void addToFavorites(int delta) {
        Integer value = favorites.getValue();
        if (value != null) favorites.setValue(value + delta);
    }

    public void addToCoins(int delta) {
        Integer value = coins.getValue();
        if (value != null) coins.setValue(value + delta);
    }

    public LiveData<Integer> getAuditStatus() {
        return auditStatus;
    }

    public LiveData<Boolean> getIsRecommend() {
        return isRecommend;
    }

    public void setIsRecommend(boolean recommend) {
        this.isRecommend.setValue(recommend);
    }

    public LiveData<Boolean> getIsTop() {
        return isTop;
    }

    public void setIsTop(boolean top) {
        this.isTop.setValue(top);
    }

    public LiveData<String> getContent() {
        return content;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
    }

    public LiveData<String> getAuthorAvatar() {
        return authorAvatar;
    }

    public LiveData<String> getAuthorUserLevelTitle() {
        return authorUserLevelTitle;
    }

    public LiveData<Integer> getAuthorMemberLevel() {
        return authorMemberLevel;
    }
}
