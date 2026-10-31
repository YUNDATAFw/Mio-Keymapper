package io.takamiya.mio.adapter;

import android.content.Context;
import android.content.Intent;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

import io.takamiya.mio.R;
import io.takamiya.mio.UserProfileActivity;
import io.takamiya.mio.bean.ForumSection;
import io.takamiya.mio.databinding.ItemForumSectionBinding;
import io.takamiya.mio.utils.RelativeTimeUtil;

public class ForumSectionAdapter extends RecyclerView.Adapter<ForumSectionAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(ForumSection section);
    }

    private final List<ForumSection> items = new ArrayList<>();
    private OnItemClickListener clickListener;

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.clickListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemForumSectionBinding binding = ItemForumSectionBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void setData(List<ForumSection> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemForumSectionBinding binding;

        ViewHolder(ItemForumSectionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(ForumSection section) {
            binding.tvSectionName.setText(section.getName());
            binding.tvSectionIntro.setText(section.getIntro());
            binding.tvCreatedAt.setText(RelativeTimeUtil.toRelativeTime(section.getCreatedAt()));
            binding.chipType.setText(section.getTypeLabel());

            Context ctx = itemView.getContext();
            String owner = section.getOwner();
            SpannableString ss = new SpannableString(owner);
            if (section.getOwnerMemberLevel() > 0 && !owner.isEmpty()) {
                ss.setSpan(new ForegroundColorSpan(
                                ContextCompat.getColor(ctx, R.color.colorPrimary)),
                        0, owner.length(),
                        SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            binding.tvOwner.setText(ss);

            View.OnClickListener toProfile = v -> {
                if (section.getOwnerUserId() > 0) {
                    Intent intent = new Intent(ctx, UserProfileActivity.class);
                    intent.putExtra(UserProfileActivity.EXTRA_USER_ID, section.getOwnerUserId());
                    intent.putExtra(UserProfileActivity.EXTRA_USERNAME, owner);
                    ctx.startActivity(intent);
                }
            };
            binding.icOwnerIcon.setOnClickListener(toProfile);
            binding.tvOwner.setOnClickListener(toProfile);

            String avatar = section.getOwnerAvatar();
            if (avatar != null && !avatar.isEmpty()) {
                Glide.with(ctx)
                        .load(avatar)
                        .placeholder(R.mipmap.ic_launcher_round)
                        .into(binding.icOwnerIcon);
            }

            itemView.setOnClickListener(v -> {
                if (clickListener != null) clickListener.onItemClick(section);
            });
        }
    }
}
