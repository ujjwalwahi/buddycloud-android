package com.buddycloud.card;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.text.Editable;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.buddycloud.FullScreenImageActivity;
import com.buddycloud.MainActivity;
import com.buddycloud.R;
import com.buddycloud.fragments.ChannelStreamFragment;
import com.buddycloud.model.MediaModel;
import com.buddycloud.model.ModelCallback;
import com.buddycloud.model.PostsModel;
import com.buddycloud.model.SubscribedChannelsModel;
import com.buddycloud.preferences.Preferences;
import com.buddycloud.utils.AvatarUtils;
import com.buddycloud.utils.ImageHelper;
import com.buddycloud.utils.InputUtils;
import com.buddycloud.utils.MeasuredMediaView;
import com.buddycloud.utils.MeasuredMediaView.MeasureListener;
import com.buddycloud.utils.TextUtils;
import com.buddycloud.utils.TimeUtils;
import com.buddycloud.utils.Typefaces;

public class PostCard extends AbstractCard {
	
	private static final String MEDIA_URL_SUFIX = "?maxwidth=600";
	private static final String MEDIA_URL_SUFIX_FULL = "?maxwidth=1024";
	
	private static final String CONTEXT_DELETE = "Delete";
	private static final String CONTEXT_SHARE = "Share";
	
	private JSONObject post;
	private String channelJid;
	private CardListAdapter repliesAdapter = new CardListAdapter();
	private Spanned anchoredContent;
	private MainActivity activity;
	private String role;
	private CardListAdapter cardAdapter;

	public PostCard(String channelJid, JSONObject post, MainActivity activity, 
			CardListAdapter cardAdapter, String role) {
		this.channelJid = channelJid;
		this.activity = activity;
		this.cardAdapter = cardAdapter;
		this.role = role;
		setPost(post);
	}

	public JSONObject getPost() {
		return post;
	}
	
	public void setPost(JSONObject post) {
		this.post = post;
		this.anchoredContent = TextUtils.anchor(post.optString("content"));
		fillReplyAdapter(this.activity);
	}

	private void fillReplyAdapter(Context context) {
		repliesAdapter.clear();
		JSONArray comments = post.optJSONArray("replies");
		for (int i = 0; i < comments.length(); i++) {
			JSONObject comment = comments.optJSONObject(i);
			repliesAdapter.addCard(toReplyCard(comment, context));
		}
		repliesAdapter.notifyDataSetChanged();
	}

	@Override
	public View getContentView(int position, View convertView, ViewGroup viewGroup) {
		
		final String postAuthor = post.optString("author");
		String published = post.optString("published");
		String mediaStr = post.optString("media");
		
		JSONArray mediaArray = null;
		if (mediaStr != null && mediaStr.length() > 0) {
			try {
				mediaArray = new JSONArray(mediaStr);
			} catch (JSONException e) {}
		}
		
		
		boolean reuse = convertView != null && convertView.getTag() != null; 
		CardViewHolder holder = null;
		
		if (!reuse) {
			LayoutInflater inflater = LayoutInflater.from(viewGroup.getContext());
			convertView = inflater.inflate(R.layout.post_entry, viewGroup, false);
			holder = fillHolder(convertView);
			convertView.setTag(holder);
		} else {
			holder = (CardViewHolder) convertView.getTag();
		}
		
		String avatarURL = AvatarUtils.avatarURL(viewGroup.getContext(), postAuthor);
		final Context context = viewGroup.getContext();
		ImageView avatarView = holder.getView(R.id.bcProfilePic);
		ImageHelper.picasso(viewGroup.getContext()).load(avatarURL)
				.placeholder(R.drawable.personal_50px)
				.error(R.drawable.personal_50px)
				.transform(ImageHelper.createRoundTransformation(context, 16, false, -1))
				.into(avatarView);
		avatarView.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				activity.showChannelFragment(postAuthor);
			}
		});
		
		TextView contentTextView = holder.getView(R.id.bcPostContent);
		TextView contentTextViewAlt = holder.getView(R.id.bcPostContentAlt);
		
		final MeasuredMediaView mediaView = holder.getView(R.id.bcImageContent);
		mediaView.setImageBitmap(null);
		
		if (mediaArray != null) {
			String apiAddress = Preferences.getPreference(context, Preferences.API_ADDRESS);
			
			JSONObject mediaJson = mediaArray.optJSONObject(0);
			
			final String userMediaURL = apiAddress + "/" + mediaJson.optString("channel") + 
					MediaModel.ENDPOINT + "/" + mediaJson.optString("id");
			final String imageURLLo = userMediaURL + MEDIA_URL_SUFIX;
			
			mediaView.setVisibility(View.VISIBLE);
			mediaView.setMeasureListener(new MeasureListener() {
				@Override
				public void measure(int widthMeasureSpec, int heightMeasureSpec) {
					ImageHelper.picasso(context)
						.load(imageURLLo)
						.transform(ImageHelper.createRoundTransformation(context, 8, 
							true, widthMeasureSpec))
						.into(mediaView);
				}
			});
			
			final String imageURLHi = userMediaURL + MEDIA_URL_SUFIX_FULL;
			ImageHelper.picasso(context).load(imageURLHi).fetch();
			
			mediaView.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					Intent intent = new Intent();
					intent.setClass(context, FullScreenImageActivity.class);
					intent.putExtra(FullScreenImageActivity.IMAGE_URL, imageURLLo);
					intent.putExtra(FullScreenImageActivity.IMAGE_URL_HIGH_RES, imageURLHi);
					activity.startActivityForResult(intent, FullScreenImageActivity.REQUEST_CODE);
				}
			});
			
			contentTextViewAlt.setVisibility(View.VISIBLE);
			contentTextView.setVisibility(View.INVISIBLE);
			contentTextViewAlt.setText(anchoredContent);
			contentTextViewAlt.setMovementMethod(LinkMovementMethod.getInstance());
		} else {
			mediaView.setVisibility(View.GONE);
			contentTextViewAlt.setVisibility(View.INVISIBLE);
			contentTextView.setVisibility(View.VISIBLE);
			contentTextView.setText(anchoredContent);
			contentTextView.setMovementMethod(LinkMovementMethod.getInstance());
		}
		
		RelativeLayout topicWrapper = holder.getView(R.id.topicWrapper);
		topicWrapper.forceLayout();
		
		try {
			long publishedTime = TimeUtils.fromISOToDate(published).getTime();
			TextView publishedView = holder.getView(R.id.bcPostDate);
			publishedView.setText(
					DateUtils.getRelativeTimeSpanString(publishedTime, 
							new Date().getTime(), DateUtils.MINUTE_IN_MILLIS));
		} catch (ParseException e) {
			e.printStackTrace();
		}
		
		View contextArrowDown = holder.getView(R.id.bcArrowDown);
		contextArrowDown.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				showPostContextActions(context);
			}
		});
		
		// Replies section
		LinearLayout commentList = holder.getView(R.id.replyListView);
		ReplySectionView.configure(commentList, repliesAdapter);
		
		// Create reply section
		View replyFrame = holder.getView(R.id.replyFrameView);
		if (!SubscribedChannelsModel.canPost(role)) {
			replyFrame.setVisibility(View.GONE);
		} else {
			replyFrame.setVisibility(View.VISIBLE);
			ImageView replyAuthorView = holder.getView(R.id.replyAuthorView);
			String replyAuthorURL = AvatarUtils.avatarURL(viewGroup.getContext(), 
					Preferences.getPreference(context, Preferences.MY_CHANNEL_JID));
			ImageHelper.picasso(viewGroup.getContext()).load(replyAuthorURL)
					.placeholder(R.drawable.personal_50px)
					.error(R.drawable.personal_50px)
					.transform(ImageHelper.createRoundTransformation(context, 16, false, -1))
					.into(replyAuthorView);
			final Button replyBtn = holder.getView(R.id.replyBtn);
			replyBtn.setTypeface(Typefaces.get(context,  "fonts/Roboto-Light.ttf"));
			final EditText replyTxt = holder.getView(R.id.replyContentTxt);
			replyBtn.setEnabled(false);
			
			replyBtn.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					reply(context, replyBtn, replyTxt);
				}
			});
			configureReplySection(replyTxt, replyBtn);
		}
		
		return convertView;
	}
	
	private void showPostContextActions(final Context context) {
		
		final List<String> contextItems = new ArrayList<String>();
		if (SubscribedChannelsModel.canDelete(role)) {
			contextItems.add(CONTEXT_DELETE);
		}
		contextItems.add(CONTEXT_SHARE);
		
		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setTitle("Post actions");
		builder.setItems(contextItems.toArray(new String[]{}), 
				new DialogInterface.OnClickListener() {
		    public void onClick(DialogInterface dialog, int item) {
		        if (contextItems.get(item).equals(CONTEXT_DELETE)) {
		        	confirmDelete(context);
		        }
		    }
		});
		AlertDialog alert = builder.create();
		alert.show();
	}
	
	private void confirmDelete(final Context context) {
		new AlertDialog.Builder(context)
				.setIcon(android.R.drawable.ic_dialog_alert)
				.setTitle("Confirm deletion")
				.setMessage("Are you sure you want to delete this post?")
				.setPositiveButton(R.string.yes,
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog,
									int which) {
								delete(context);
							}
						}).setNegativeButton(R.string.no, null).show();
	}
	
	private void delete(final Context context) {
		final String postId = post.optString("id");
		PostsModel.getInstance().delete(context, new ModelCallback<Void>() {
			@Override
			public void success(Void response) {
				cardAdapter.remove(postId);
				Toast.makeText(context, "Post deleted",
						Toast.LENGTH_LONG).show();
			}

			@Override
			public void error(Throwable throwable) {
				Toast.makeText(context, "Could not delete post.",
						Toast.LENGTH_LONG).show();
			}
		}, channelJid, postId);
	}
	
	private void reply(final Context context,
			final Button replyBtn, final EditText replyTxt) {
		
		if (!replyBtn.isEnabled()) {
			return;
		}
		
		JSONObject replyPost = createReply(replyTxt);
		replyTxt.setText("");
		InputUtils.hideKeyboard(context, replyTxt);
		PostsModel.getInstance().save(context, replyPost, new ModelCallback<JSONObject>() {
			@Override
			public void success(JSONObject response) {
				Toast.makeText(context, "Post created", Toast.LENGTH_LONG).show();
				loadReplies(post, channelJid, context, new ModelCallback<Void>() {
					@Override
					public void success(Void response) {
						getParentAdapter().sort();
						ChannelStreamFragment fragment = (ChannelStreamFragment) getParentAdapter().getFragment();
						fragment.scrollUp();
					}

					@Override
					public void error(Throwable throwable) {}
				});
			}
			
			@Override
			public void error(Throwable throwable) {
				Toast.makeText(context, throwable.getMessage(), Toast.LENGTH_LONG).show();
			}
		}, channelJid);
	}
	
	public static void configureReplySection(final EditText replyContent, final Button replyBtn) {
		replyContent.addTextChangedListener(new TextWatcher() {

			@Override
			public void afterTextChanged(Editable arg0) {
				
			}

			@Override
			public void beforeTextChanged(CharSequence arg0, int arg1,
					int arg2, int arg3) {
				
			}

			@Override
			public void onTextChanged(CharSequence arg0, int arg1, int arg2,
					int arg3) {
				boolean enabled = arg0 != null && arg0.length() > 0;
				replyBtn.setEnabled(enabled);
			}
			
		});
	}
	
	private JSONObject createReply(EditText postContent) {
		JSONObject reply = new JSONObject();
		try {
			reply.putOpt("content", postContent.getText().toString());
			reply.putOpt("replyTo", this.post.optString("id"));
		} catch (JSONException e) {
			e.printStackTrace();
		}
		return reply;
	}
	
	private void loadReplies(JSONObject post, String channelJid, 
			final Context context, final ModelCallback<Void> callback) {
		PostsModel.getInstance().fetchSinglePost(context, new ModelCallback<JSONObject>() {
			@Override
			public void success(JSONObject response) {
				setPost(response);
				callback.success(null);
			}
			
			@Override
			public void error(Throwable throwable) {
				// TODO Auto-generated method stub
			}
		}, channelJid, post.optString("id"));
	}
	
	private CommentCard toReplyCard(JSONObject comment, Context context) {
		String postAuthor = comment.optString("author");
		String postContent = comment.optString("content");
		String published = comment.optString("published");
		CommentCard commentCard = new CommentCard(postAuthor, postContent, published, activity);
		commentCard.setPost(comment);
		return commentCard;
	}
	
	private static CardViewHolder fillHolder(View view) {
		return CardViewHolder.create(view, 
				R.id.bcPostContentAlt, R.id.bcProfilePic, 
				R.id.bcPostContent, R.id.bcCommentCount, 
				R.id.bcImageContent, R.id.bcPostDate, 
				R.id.replyListView, R.id.replyAuthorView,
				R.id.replyContentTxt, R.id.replyBtn, 
				R.id.topicWrapper, R.id.replyFrameView, 
				R.id.bcArrowDown);
	}

	@Override
	public int compareTo(Card anotherCard) {
		try {
			Date otherUpdated = TimeUtils.updated(anotherCard.getPost());
			Date thisUpdated = TimeUtils.updated(this.getPost());
			return otherUpdated.compareTo(thisUpdated);
		} catch (ParseException e) {
			return 0;
		}
	}
}
