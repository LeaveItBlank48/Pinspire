package com.example.pinspire;

import android.app.*;
import android.os.Bundle;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.text.TextUtils;
import android.view.inputmethod.EditorInfo;
import java.net.*;
import java.io.*;
import java.util.concurrent.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;

/** Small offline Pinterest-inspired demo. No accounts, cloud or external libraries required. */
public class MainActivity extends Activity {
    private static final int PICK_IMAGE = 42;
    private Post editingPost = null;
    private final ExecutorService io = Executors.newFixedThreadPool(4);
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final LruCache<String,Bitmap> imageCache = new LruCache<String,Bitmap>(36 * 1024) {
        @Override protected int sizeOf(String k,Bitmap b){return Math.max(1,b.getByteCount()/1024);}
    };
    private final ArrayList<Post> discovery = new ArrayList<>();
    private final HashSet<String> seenRemote = new HashSet<>();
    private final HashMap<String,Integer> interestScores = new HashMap<>();
    private final String[] categories = {"All", "Architecture", "Interiors", "Fashion", "Art", "Photography", "Nature", "Food", "Design"};
    private String activeCategory="All", onlineSearch="";
    private boolean loading=false, hasMore=true, exploreOnly=false;
    private int feedGeneration=0;
    private final HashMap<String,Integer> categoryOffsets = new HashMap<>();
    private int remotePage=0;
    private ScrollView mainScroll;
    private TextView feedStatus;
    private static final String API = "https://pixabay.com/api/";
    private final ArrayList<String> selectedImages = new ArrayList<>();
    private final ArrayList<Post> posts = new ArrayList<>();
    private final ArrayList<String> boards = new ArrayList<>();
    private final HashMap<String,ArrayList<String>> saved = new HashMap<>();
    private LinearLayout root, content;
    private String currentScreen = "home", search = "", chosenImage = "";
    private int nextId = 1;
    private EditText createTitle, createDescription;
    private TextView imageSelection;
    private final int ink = Color.rgb(27,27,27), accent = Color.rgb(27,27,27), pale = Color.rgb(246,246,244);

    static class Post {
        String id,title,description,uri,category="Other",source="",creator="",license="";
        boolean remote=false;
        ArrayList<String> images = new ArrayList<>(); int color;
        Post(String i,String t,String d,String u,int c){id=i;title=t;description=d;uri=u;color=c;if(u!=null&&!u.isEmpty())images.add(u);}
    }

    @Override public void onCreate(Bundle b){super.onCreate(b); load(); loadInterests(); home();}
    @Override protected void onDestroy(){io.shutdownNow();super.onDestroy();}
    private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
    private GradientDrawable bg(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    private GradientDrawable outlined(){GradientDrawable g=bg(Color.WHITE,12);g.setStroke(dp(1),0xffe6e6e3);return g;}
    private TextView text(String value,int size,boolean bold){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(ink);if(bold)v.setTypeface(null,Typeface.BOLD);return v;}
    private LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private void pad(View v,int a){v.setPadding(dp(a),dp(a),dp(a),dp(a));}
    private void gap(LinearLayout l,int height){View s=new View(this);l.addView(s,new LinearLayout.LayoutParams(1,dp(height)));}
    private void button(LinearLayout parent,String label,Runnable action){Button b=new Button(this);b.setAllCaps(false);b.setText(label);b.setTextColor(Color.WHITE);b.setBackground(bg(accent,9));parent.addView(b,new LinearLayout.LayoutParams(-1,dp(48)));b.setOnClickListener(v->action.run());gap(parent,8);}
    private EditText field(LinearLayout parent,String hint){EditText e=new EditText(this);e.setSingleLine(true);e.setHint(hint);e.setTextSize(16);parent.addView(e,new LinearLayout.LayoutParams(-1,dp(55)));gap(parent,9);return e;}
    private void shell(String title){
        root=vertical();root.setBackgroundColor(Color.WHITE);setContentView(root);
        LinearLayout header=vertical();header.setPadding(dp(20),dp(18),dp(20),dp(13));header.setBackgroundColor(Color.WHITE);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        TextView name=text("pinspire.",30,true);name.setLetterSpacing(-0.06f);
        name.setContentDescription("Pinspire, go to fresh Discover home");
        name.setOnClickListener(v->freshHome());
        top.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        TextView monogram=text("◉",22,true);monogram.setGravity(Gravity.CENTER);monogram.setBackground(outlined());top.addView(monogram,new LinearLayout.LayoutParams(dp(42),dp(42)));
        header.addView(top);
        if(!"home".equals(currentScreen)){TextView subtitle=text(title,13,false);subtitle.setTextColor(0xff858585);header.addView(subtitle);}
        root.addView(header);
        View line=new View(this);line.setBackgroundColor(0xffeeeeeb);root.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));
        ScrollView scroll=new ScrollView(this);mainScroll=scroll;
        scroll.setOnScrollChangeListener((View.OnScrollChangeListener)(v,sx,sy,ox,oy)->{
            if("home".equals(currentScreen) && !loading && hasMore &&
                sy + scroll.getHeight() >= content.getHeight() - dp(650)) requestMore();
        });
        scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);
        content=vertical();content.setPadding(dp(16),dp(18),dp(16),dp(20));scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        View line2=new View(this);line2.setBackgroundColor(0xffeeeeeb);root.addView(line2,new LinearLayout.LayoutParams(-1,dp(1)));
        LinearLayout nav=new LinearLayout(this);nav.setBackgroundColor(Color.WHITE);
        String[] labels={"Discover","Collections","Create"};String[] symbols={"⌕","▦","+"};
        Runnable[] actions={()->freshHome(),()->boards(),()->create()};
        for(int i=0;i<3;i++){final int n=i;LinearLayout tab=vertical();tab.setGravity(Gravity.CENTER);TextView icon=text(symbols[i],23,false);icon.setGravity(Gravity.CENTER);tab.addView(icon);TextView caption=text(labels[i],11,false);caption.setGravity(Gravity.CENTER);caption.setTextColor(0xff777777);tab.addView(caption);nav.addView(tab,new LinearLayout.LayoutParams(0,dp(65),1));tab.setOnClickListener(v->actions[n].run());}
        root.addView(nav);
    }
    private View art(Post p,int height){
        FrameLayout frame=new FrameLayout(this);frame.setBackground(bg(p.color,5));frame.setClipToOutline(true);
        if(!p.images.isEmpty()){
            try{ImageView iv=new ImageView(this);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                if(p.images.get(0).startsWith("https://")) loadRemoteImage(iv,p.images.get(0));
                else iv.setImageURI(Uri.parse(p.images.get(0)));
                frame.addView(iv,new FrameLayout.LayoutParams(-1,-1));}
            catch(Exception ignored){}
        }else{
            // Abstract editorial artwork for built-in demo posts. User-selected images display normally.
            View illustration=new View(this){android.graphics.Paint paint=new android.graphics.Paint(3);
                @Override protected void onDraw(android.graphics.Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight();
                    paint.setColor(0x44ffffff);c.drawCircle(w*.78f,h*.25f,w*.38f,paint);
                    paint.setColor(0x66ffffff);c.drawRoundRect(w*.10f,h*.48f,w*.78f,h*.86f,dp(14),dp(14),paint);
                    paint.setColor(0x55777777);c.drawCircle(w*.34f,h*.40f,w*.17f,paint);
                    paint.setColor(0x99ffffff);c.drawRect(w*.12f,h*.70f,w*.66f,h*.73f,paint);
                }};frame.addView(illustration,new FrameLayout.LayoutParams(-1,-1));
        }
        frame.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(height)));return frame;
    }
    /** Tapping the Pinspire brand or bottom Discover tab returns to the default For You home. */
    private void freshHome(){
        resetToDiscover(false);
    }
    /** Explore random starts a fresh discovery feed; it does not carry over search filters. */
    private void resetToDiscover(boolean randomMode){
        activeCategory="All";
        onlineSearch="";
        exploreOnly=randomMode;
        // Invalidate pending network pages before rebuilding the screen.
        feedGeneration++;
        loading=false;
        discovery.clear();
        seenRemote.clear();
        categoryOffsets.clear();
        remotePage=0;
        hasMore=true;
        home(); // Shows a cleared search field, All category, and requests page one.
        if(mainScroll!=null)mainScroll.post(()->mainScroll.scrollTo(0,0));
    }
    private void home(){currentScreen="home";shell("Discover and collect your favorite ideas");
        content.addView(text("Discover",25,true));gap(content,4);
        TextView hint=text("Fresh ideas, saved your way.",13,false);hint.setTextColor(0xff888888);content.addView(hint);gap(content,12);
        EditText query=field(content,"Search online: interiors, streetwear, art...");
        query.setText(onlineSearch);query.setBackground(outlined());query.setPadding(dp(14),0,dp(14),0);query.setTextSize(14);
        query.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        query.setOnEditorActionListener((v,action,event)->{
            if(action==EditorInfo.IME_ACTION_SEARCH || (event!=null && event.getKeyCode()==KeyEvent.KEYCODE_ENTER)){
                onlineSearch=query.getText().toString().trim();activeCategory="All";resetDiscovery();
                ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(query.getWindowToken(),0);
                return true;
            }return false;
        });
        LinearLayout controls=new LinearLayout(this);
        Button searchButton=new Button(this);searchButton.setAllCaps(false);searchButton.setText("Search");
        controls.addView(searchButton,new LinearLayout.LayoutParams(0,dp(44),1));
        Button clearButton=new Button(this);clearButton.setAllCaps(false);clearButton.setText("New mix");
        controls.addView(clearButton,new LinearLayout.LayoutParams(0,dp(44),1));content.addView(controls);
        searchButton.setOnClickListener(v->{onlineSearch=query.getText().toString().trim();activeCategory="All";resetDiscovery();});
        clearButton.setOnClickListener(v->resetToDiscover(exploreOnly));
        LinearLayout modeLine=new LinearLayout(this);
        Button forYou=new Button(this);forYou.setAllCaps(false);forYou.setText(exploreOnly?"For you":"For you ✓");
        modeLine.addView(forYou,new LinearLayout.LayoutParams(0,dp(42),1));
        Button exploreButton=new Button(this);exploreButton.setAllCaps(false);exploreButton.setText(exploreOnly?"Explore ✓":"Explore random");
        modeLine.addView(exploreButton,new LinearLayout.LayoutParams(0,dp(42),1));
        content.addView(modeLine);
        forYou.setOnClickListener(v->freshHome());
        exploreButton.setOnClickListener(v->resetToDiscover(true));
        gap(content,8);
        HorizontalScrollView chipScroll=new HorizontalScrollView(this);chipScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chips=new LinearLayout(this);chips.setOrientation(LinearLayout.HORIZONTAL);chipScroll.addView(chips);
        for(String c:categories){TextView chip=text(c,13,c.equals(activeCategory));
            chip.setPadding(dp(13),dp(11),dp(13),dp(11));chip.setBackground(c.equals(activeCategory)?bg(0xffecece9,14):bg(0xfff7f7f5,14));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.setMargins(0,0,dp(7),0);chips.addView(chip,lp);
            chip.setOnClickListener(v->{onlineSearch="";activeCategory=c;home();resetDiscovery();});
        }content.addView(chipScroll);gap(content,14);
        feedHolder=vertical();content.addView(feedHolder);
        feedStatus=text("",12,false);feedStatus.setGravity(Gravity.CENTER);feedStatus.setTextColor(0xff888888);
        content.addView(feedStatus);gap(content,15);
        Button loadButton=new Button(this);loadButton.setText("Load more inspiration");loadButton.setAllCaps(false);
        content.addView(loadButton,new LinearLayout.LayoutParams(-1,dp(45)));
        loadButton.setOnClickListener(v->requestMore());
        renderFeed();
        if(discovery.isEmpty() && !loading && hasMore)requestMore();
    }
    private LinearLayout feedHolder;
    private void renderFeed(){if(feedHolder==null||!"home".equals(currentScreen))return;
        feedHolder.removeAllViews();
        ArrayList<Post> filtered=new ArrayList<>();
        // User-created posts are always displayed first on the general feed.
        if(activeCategory.equals("All") && onlineSearch.isEmpty())for(Post p:posts)if(!p.remote)filtered.add(p);
        // Save snapshots are intentionally not duplicated in the live discovery feed.
        filtered.addAll(discovery);
        if(filtered.isEmpty()){TextView empty=text("Finding inspiration... Make sure your phone has internet access.",14,false);empty.setTextColor(0xff888888);feedHolder.addView(empty);}
        for(int i=0;i<filtered.size();i+=2){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.TOP);feedHolder.addView(row);
            for(int j=i;j<Math.min(i+2,filtered.size());j++){
                Post p=filtered.get(j);LinearLayout card=vertical();card.setBackgroundColor(Color.WHITE);pad(card,2);
                LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);cp.setMargins(dp(3),dp(3),dp(3),dp(10));row.addView(card,cp);
                card.addView(art(p,j%4==0?240:j%3==0?205:175));gap(card,7);
                TextView label=text(p.title,13,true);label.setMaxLines(2);label.setEllipsize(TextUtils.TruncateAt.END);card.addView(label);
                if(p.remote){TextView subtitle=text(p.category,11,false);subtitle.setTextColor(0xff888888);card.addView(subtitle);}
                card.setOnClickListener(v->{track(p,"view");details(p);});
            }
        }
        if(feedStatus!=null){if(loading)feedStatus.setText("Loading fresh images...");
            else if(!hasMore)feedStatus.setText("No more images for now. Try another category.");
            else feedStatus.setText("Scroll down for more images • Online discovery powered by Pixabay");}
    }
    private void details(Post p){currentScreen="detail";shell("Post details");
        if(p.images.size()>1){
            // Swipe horizontally on the large image. Vertical swipes still scroll the post page.
            TextView counter=text("1 / "+p.images.size()+"  ·  Swipe left or right",13,false);
            counter.setTextColor(0xff777777);
            counter.setGravity(Gravity.CENTER);
            content.addView(counter);gap(content,9);
            FrameLayout gallery=new FrameLayout(this);
            gallery.setBackground(bg(p.color,8));
            content.addView(gallery,new LinearLayout.LayoutParams(-1,dp(370)));
            ImageView image=new ImageView(this);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            gallery.addView(image,new FrameLayout.LayoutParams(-1,-1));
            TextView indicator=text("",13,true);
            indicator.setTextColor(Color.WHITE);
            indicator.setGravity(Gravity.CENTER);
            indicator.setBackground(bg(0xaa1b1b1b,14));
            FrameLayout.LayoutParams badgeParams=new FrameLayout.LayoutParams(dp(64),dp(32),Gravity.BOTTOM|Gravity.RIGHT);
            badgeParams.setMargins(0,0,dp(12),dp(12));
            gallery.addView(indicator,badgeParams);
            final int[] index={0};
            Runnable update=()->{
                String imagePath=p.images.get(index[0]);
                image.setImageDrawable(null);
                if(imagePath.startsWith("https://"))loadRemoteImage(image,imagePath);
                else image.setImageURI(Uri.parse(imagePath));
                counter.setText((index[0]+1)+" / "+p.images.size()+"  ·  Swipe left or right");
                indicator.setText((index[0]+1)+" / "+p.images.size());
            };
            update.run();
            final float[] downX={0}, downY={0};
            image.setOnTouchListener((v,event)->{
                switch(event.getActionMasked()){
                    case MotionEvent.ACTION_DOWN:
                        downX[0]=event.getX();downY[0]=event.getY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if(Math.abs(event.getX()-downX[0])>dp(12)
                           && Math.abs(event.getX()-downX[0])>Math.abs(event.getY()-downY[0])){
                            v.getParent().requestDisallowInterceptTouchEvent(true);
                            if(mainScroll!=null)mainScroll.requestDisallowInterceptTouchEvent(true);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        float dx=event.getX()-downX[0];
                        float dy=event.getY()-downY[0];
                        if(Math.abs(dx)>dp(45) && Math.abs(dx)>Math.abs(dy)*1.15f){
                            if(dx<0 && index[0]<p.images.size()-1)index[0]++;
                            else if(dx>0 && index[0]>0)index[0]--;
                            update.run();
                        }
                        return true;
                    case MotionEvent.ACTION_CANCEL:return true;
                }
                return true;
            });
            gap(content,10);
            // Thumbnail strip offers another way to select an image in a multi-image post.
            HorizontalScrollView thumbnails=new HorizontalScrollView(this);
            thumbnails.setHorizontalScrollBarEnabled(false);
            LinearLayout thumbRow=new LinearLayout(this);
            for(int i=0;i<p.images.size();i++){
                final int chosen=i;
                FrameLayout thumb=new FrameLayout(this);
                LinearLayout.LayoutParams thumbParams=new LinearLayout.LayoutParams(dp(64),dp(64));
                thumbParams.setMargins(0,0,dp(8),0);
                thumbRow.addView(thumb,thumbParams);
                ImageView small=new ImageView(this);
                small.setScaleType(ImageView.ScaleType.CENTER_CROP);
                small.setBackground(bg(0xffeeeeeb,6));
                thumb.addView(small,new FrameLayout.LayoutParams(-1,-1));
                String u=p.images.get(i);
                if(u.startsWith("https://"))loadRemoteImage(small,u);
                else small.setImageURI(Uri.parse(u));
                thumb.setOnClickListener(v->{index[0]=chosen;update.run();});
            }
            thumbnails.addView(thumbRow);
            content.addView(thumbnails);
        }else content.addView(art(p,360));
        gap(content,15);content.addView(text(p.title,24,true));gap(content,8);content.addView(text(p.description,16,false));gap(content,20);
        if(p.remote){TextView attribution=text("Image: "+p.creator+" • "+p.license,12,false);attribution.setTextColor(0xff777777);content.addView(attribution);gap(content,8);
            if(!p.source.isEmpty())button(content,"View original on Pixabay",()->{
                Intent intent=new Intent(Intent.ACTION_VIEW,Uri.parse(p.source));startActivity(intent);
            });
            button(content,"Not interested in "+p.category,()->{track(p,"negative");home();});
        }
        button(content,"+ Save to collection",()->chooseBoard(p));
        if(!p.remote)button(content,"Edit post",()->editPost(p));
        if(!p.remote)button(content,"Delete post",()->new AlertDialog.Builder(this).setTitle("Delete post?")
            .setMessage("This permanently deletes the post and removes it from all collections.")
            .setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->{posts.remove(p);for(ArrayList<String> ids:saved.values())ids.remove(p.id);persist();home();}).show());
        button(content,"Back to explore",()->home());
    }
    private void chooseBoard(Post p){if(boards.isEmpty()){new AlertDialog.Builder(this).setMessage("Create a board first!").setPositiveButton("Create",(d,w)->newBoard()).setNegativeButton("Cancel",null).show();return;}
        new AlertDialog.Builder(this).setTitle("Save to which board?").setItems(boards.toArray(new String[0]),(d,which)->{String board=boards.get(which);ArrayList<String> ids=saved.get(board);if(!ids.contains(p.id))ids.add(p.id);
            if(p.remote && find(p.id)==null){
                // Pixabay search-image URLs expire: save an app-private copy instead of hotlinking indefinitely.
                io.execute(()->{
                    try{
                        File local=downloadImage(p.uri,p.id);
                        ui.post(()->{
                            p.uri=Uri.fromFile(local).toString();p.images.clear();p.images.add(p.uri);
                            if(find(p.id)==null)posts.add(p);
                            track(p,"save");persist();Toast.makeText(this,"Saved to "+board,Toast.LENGTH_SHORT).show();
                        });
                    }catch(Exception ex){ui.post(()->{ids.remove(p.id);Toast.makeText(this,"Image could not be saved. Try again.",Toast.LENGTH_LONG).show();});}
                });
            }else{track(p,"save");persist();Toast.makeText(this,"Saved to "+board,Toast.LENGTH_SHORT).show();}}).show();
    }
    private void boards(){currentScreen="boards";shell("Your saved collections");content.addView(text("Your collections",25,true));gap(content,14);button(content,"+ New collection",()->newBoard());
        if(boards.isEmpty())content.addView(text("No boards yet. Create your first board!",16,false));
        for(String board:boards){LinearLayout card=vertical();pad(card,15);card.setBackground(outlined());content.addView(card);card.addView(text(board,20,true));card.addView(text(saved.get(board).size()+" saved posts",14,false));card.setOnClickListener(v->boardDetail(board));gap(content,10);}
    }
    private void newBoard(){EditText input=new EditText(this);input.setSingleLine(true);input.setHint("e.g. Dream Apartment");
        new AlertDialog.Builder(this).setTitle("Create a board").setView(input).setPositiveButton("Create",(d,w)->{String name=input.getText().toString().trim();if(name.isEmpty()){Toast.makeText(this,"Enter a board name",Toast.LENGTH_SHORT).show();return;}if(boards.contains(name)){Toast.makeText(this,"Board already exists",Toast.LENGTH_SHORT).show();return;}boards.add(name);saved.put(name,new ArrayList<>());persist();boards();}).setNegativeButton("Cancel",null).show();
    }
    private void boardDetail(String board){currentScreen="board";shell("Board: "+board);button(content,"Back to boards",()->boards());
        button(content,"Add posts to this collection",()->addToBoard(board));
        button(content,"Rename collection",()->renameBoard(board));
        button(content,"Delete collection",()->new AlertDialog.Builder(this).setTitle("Delete collection?")
            .setMessage("The collection will be removed, but its posts will remain available.")
            .setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->{boards.remove(board);saved.remove(board);persist();boards();}).show());
        int count=0;for(String id:saved.get(board)){Post p=find(id);if(p==null)continue;count++;LinearLayout card=vertical();pad(card,10);card.setBackground(outlined());content.addView(card);card.addView(art(p,160));gap(card,8);card.addView(text(p.title,18,true));button(card,"Open post",()->details(p));button(card,"Remove from board",()->{saved.get(board).remove(p.id);persist();boardDetail(board);});gap(content,12);}
        if(count==0)content.addView(text("No saved posts yet. Explore the feed and save something!",16,false));
    }
    private Post find(String id){for(Post p:posts)if(p.id.equals(id))return p;return null;}
    private void addToBoard(String board){
        if(posts.isEmpty()){Toast.makeText(this,"No posts available",Toast.LENGTH_SHORT).show();return;}
        String[] names=new String[posts.size()];boolean[] selected=new boolean[posts.size()];
        for(int i=0;i<posts.size();i++){names[i]=posts.get(i).title;selected[i]=saved.get(board).contains(posts.get(i).id);}
        new AlertDialog.Builder(this).setTitle("Choose posts for "+board).setMultiChoiceItems(names,selected,(d,i,on)->selected[i]=on)
            .setPositiveButton("Save",(d,w)->{ArrayList<String> ids=saved.get(board);ids.clear();for(int i=0;i<posts.size();i++)if(selected[i])ids.add(posts.get(i).id);persist();boardDetail(board);})
            .setNegativeButton("Cancel",null).show();
    }
    private void renameBoard(String old){EditText input=new EditText(this);input.setSingleLine(true);input.setText(old);
        new AlertDialog.Builder(this).setTitle("Rename collection").setView(input)
            .setPositiveButton("Save",(d,w)->{String name=input.getText().toString().trim();if(name.isEmpty()||(boards.contains(name)&&!name.equals(old))){Toast.makeText(this,"Invalid or duplicate name",Toast.LENGTH_LONG).show();return;}
                int index=boards.indexOf(old);boards.set(index,name);saved.put(name,saved.remove(old));persist();boardDetail(name);})
            .setNegativeButton("Cancel",null).show();
    }
    private void create(){editingPost=null;selectedImages.clear();showEditor();}
    private void editPost(Post p){editingPost=p;selectedImages.clear();selectedImages.addAll(p.images);showEditor();}
    private void showEditor(){currentScreen="create";shell(editingPost==null?"Create post":"Edit post");
        content.addView(text(editingPost==null?"New inspiration":"Edit inspiration",25,true));gap(content,16);
        createTitle=field(content,"Post title (required)");createDescription=field(content,"Description (required)");
        if(editingPost!=null){createTitle.setText(editingPost.title);createDescription.setText(editingPost.description);}
        imageSelection=text("",13,false);content.addView(imageSelection);updateImageCount();gap(content,10);
        button(content,"Add images from gallery",()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);startActivityForResult(i,PICK_IMAGE);});
        button(content,"Remove selected images",()->removeImages());
        button(content,editingPost==null?"Publish inspiration":"Save changes",()->{
            String title=createTitle.getText().toString().trim(),description=createDescription.getText().toString().trim();
            if(title.isEmpty()||description.isEmpty()){Toast.makeText(this,"Enter a title and description",Toast.LENGTH_LONG).show();return;}
            if(editingPost==null){int[] colors={0xff8a9bbd,0xffcf9f8a,0xff8da89a,0xffbba0bd};Post p=new Post("p"+(nextId++),title,description,"",colors[posts.size()%colors.length]);p.images.addAll(selectedImages);p.uri=p.images.isEmpty()?"":p.images.get(0);posts.add(0,p);}
            else{editingPost.title=title;editingPost.description=description;editingPost.images.clear();editingPost.images.addAll(selectedImages);editingPost.uri=editingPost.images.isEmpty()?"":editingPost.images.get(0);}
            persist();Toast.makeText(this,"Post saved!",Toast.LENGTH_SHORT).show();home();});
    }
    private void updateImageCount(){if(imageSelection!=null)imageSelection.setText(selectedImages.size()+" image(s) selected. You can add more.");}
    private void removeImages(){if(selectedImages.isEmpty()){Toast.makeText(this,"No images to remove",Toast.LENGTH_SHORT).show();return;}
        String[] labels=new String[selectedImages.size()];boolean[] remove=new boolean[labels.length];for(int i=0;i<labels.length;i++)labels[i]="Image "+(i+1);
        new AlertDialog.Builder(this).setTitle("Remove images").setMultiChoiceItems(labels,remove,(d,i,v)->remove[i]=v)
            .setPositiveButton("Remove",(d,w)->{for(int i=remove.length-1;i>=0;i--)if(remove[i])selectedImages.remove(i);updateImageCount();})
            .setNegativeButton("Cancel",null).show();
    }
    private void addImageUri(Uri u){if(u==null)return;try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
        String value=u.toString();if(!selectedImages.contains(value))selectedImages.add(value);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
        if(request==PICK_IMAGE&&result==RESULT_OK&&data!=null){
            if(data.getClipData()!=null){for(int i=0;i<data.getClipData().getItemCount();i++)addImageUri(data.getClipData().getItemAt(i).getUri());}
            else addImageUri(data.getData());updateImageCount();
        }
    }
    private void seed(){String[] titles={"Cozy Reading Corner","Weekend Travel","Minimal Outfit","Coffee Inspiration","Green Kitchen","Creative Workspace"};String[] desc={"A warm, comfortable corner for reading.","Ideas for a peaceful weekend trip.","Simple everyday fashion inspiration.","A cozy café atmosphere.","Plants and natural colors for a kitchen.","A tidy desk for creative projects."};int[] colors={0xffb58b73,0xff7299b2,0xffa88c9d,0xffb99d79,0xff87a28b,0xff9693b6};for(int i=0;i<titles.length;i++)posts.add(new Post("p"+(nextId++),titles[i],desc[i],"",colors[i]));boards.add("Favorites");saved.put("Favorites",new ArrayList<>());}
    private void persist(){try{JSONObject obj=new JSONObject();obj.put("nextId",nextId);JSONArray a=new JSONArray();for(Post p:posts){JSONObject j=new JSONObject();j.put("id",p.id);j.put("title",p.title);j.put("description",p.description);j.put("uri",p.uri);j.put("images",new JSONArray(p.images));j.put("color",p.color);j.put("remote",p.remote);j.put("source",p.source);j.put("category",p.category);j.put("creator",p.creator);j.put("license",p.license);a.put(j);}obj.put("posts",a);JSONArray b=new JSONArray();for(String name:boards){JSONObject j=new JSONObject();j.put("name",name);j.put("saved",new JSONArray(saved.get(name)));b.put(j);}obj.put("boards",b);getPreferences(MODE_PRIVATE).edit().putString("data",obj.toString()).apply();}catch(Exception e){Toast.makeText(this,"Could not save data",Toast.LENGTH_SHORT).show();}}
    private void load(){String raw=getPreferences(MODE_PRIVATE).getString("data",null);if(raw==null){seed();persist();return;}try{JSONObject obj=new JSONObject(raw);nextId=obj.optInt("nextId",1);JSONArray a=obj.getJSONArray("posts");for(int i=0;i<a.length();i++){JSONObject j=a.getJSONObject(i);Post p=new Post(j.getString("id"),j.getString("title"),j.getString("description"),j.optString("uri",""),j.getInt("color"));JSONArray imgs=j.optJSONArray("images");if(imgs!=null){p.images.clear();for(int z=0;z<imgs.length();z++)p.images.add(imgs.optString(z));}p.remote=j.optBoolean("remote",false);p.source=j.optString("source","");p.category=j.optString("category","Other");p.creator=j.optString("creator","");p.license=j.optString("license","");posts.add(p);}JSONArray b=obj.getJSONArray("boards");for(int i=0;i<b.length();i++){JSONObject j=b.getJSONObject(i);String name=j.getString("name");boards.add(name);ArrayList<String> ids=new ArrayList<>();JSONArray arr=j.getJSONArray("saved");for(int k=0;k<arr.length();k++)ids.add(arr.getString(k));saved.put(name,ids);}}catch(Exception e){posts.clear();boards.clear();saved.clear();seed();persist();}}
    // ----- Online discovery and simple on-device personalization -----
    private void resetDiscovery(){
        feedGeneration++;discovery.clear();seenRemote.clear();categoryOffsets.clear();remotePage=0;hasMore=true;loading=false;
        renderFeed();requestMore();
    }
    private void loadInterests(){
        try{JSONObject j=new JSONObject(getPreferences(MODE_PRIVATE).getString("interests","{}"));
            for(Iterator<String> it=j.keys();it.hasNext();){String k=it.next();interestScores.put(k,j.optInt(k));}
        }catch(Exception ignored){}
    }
    private void track(Post p,String event){
        if(p.category.equals("Other"))return;
        int delta=event.equals("save")?5:event.equals("negative")?-6:1;
        int next=Math.max(-20,Math.min(100,interestScores.getOrDefault(p.category,0)+delta));
        interestScores.put(p.category,next);
        JSONObject j=new JSONObject();try{for(String key:interestScores.keySet())j.put(key,interestScores.get(key));}catch(Exception ignored){}
        getPreferences(MODE_PRIVATE).edit().putString("interests",j.toString()).apply();
    }
    private String pickCategory(){
        if(!onlineSearch.isEmpty())return onlineSearch;
        if(!activeCategory.equals("All"))return activeCategory;
        String[] options=Arrays.copyOfRange(categories,1,categories.length);
        Random r=new Random();
        // 30% exploration, 70% weighted by interaction history.
        if(exploreOnly || r.nextFloat()<.30f)return options[r.nextInt(options.length)];
        int total=0;for(String c:options)total+=Math.max(1,1+interestScores.getOrDefault(c,0));
        int pick=r.nextInt(Math.max(1,total));
        for(String c:options){pick-=Math.max(1,1+interestScores.getOrDefault(c,0));if(pick<0)return c;}
        return options[0];
    }
    private void requestMore(){
        if(loading || !hasMore)return;
        loading=true;final String category=pickCategory();
        final String requestSearch=onlineSearch;final String requestCategory=activeCategory;
        final int requestedPage=++remotePage;
        final int generation=feedGeneration;
        final int offset=categoryOffsets.getOrDefault(category,0);
        categoryOffsets.put(category,offset+20);
        if(feedStatus!=null)feedStatus.setText("Loading images for "+category+"...");
        io.execute(()->{
            ArrayList<Post> results=new ArrayList<>();String failure="";
            try{
                if(BuildConfig.PIXABAY_API_KEY.isEmpty())throw new IOException("Pixabay key missing: add pixabayApiKey to local.properties");
                String term=category.equals("Interiors")?"interior design":category.equals("Design")?"graphic design":category;
                // Explicit searches are kept intact, so a niche query is not replaced by unrelated fallback images.
                String typeCategory="";
                String lowered=term.toLowerCase(Locale.US);
                if(lowered.contains("streetwear")||lowered.contains("fashion")||lowered.contains("outfit")||lowered.contains("clothing"))typeCategory="fashion";
                else if(lowered.contains("food")||lowered.contains("recipe"))typeCategory="food";
                else if(lowered.contains("architecture")||lowered.contains("interior"))typeCategory="buildings";
                else if(lowered.contains("nature"))typeCategory="nature";
                final int page=(offset/20)+1;
                String endpoint=API+"?key="+URLEncoder.encode(BuildConfig.PIXABAY_API_KEY,"UTF-8")+
                    "&q="+URLEncoder.encode(term,"UTF-8")+"&image_type=photo&safesearch=true"+
                    "&per_page=20&page="+page+"&order=popular"+
                    (typeCategory.isEmpty()?"":"&category="+typeCategory);
                // Pixabay requires API response caching for 24 hours.
                JSONObject data=fetchPixabayCached(endpoint);
                {
                    JSONArray hits=data.optJSONArray("hits");
                    if(hits!=null){
                        for(int i=0;i<hits.length();i++){
                            JSONObject hit=hits.optJSONObject(i);if(hit==null)continue;
                            String url=hit.optString("webformatURL", "");
                            if(!url.startsWith("https://"))continue;
                            String tags=hit.optString("tags","");
                            String id="pixabay-"+hit.optLong("id",0);
                            if(id.equals("pixabay-0"))continue;
                            String title=tags.isEmpty()?"Photo inspiration":tags.split(",")[0].trim();
                            Post p=new Post(id,title,"Tags: "+tags,url,0xffe7e7e5);
                            p.remote=true;p.category=requestSearch.isEmpty()?category:requestSearch;
                            p.source=hit.optString("pageURL","");
                            p.creator=hit.optString("user","Pixabay contributor");
                            p.license="Pixabay Content License";
                            results.add(p);
                        }
                    }
                }
            }catch(Exception e){failure=e.getClass().getSimpleName()+": "+e.getMessage();}
            final String error=failure;
            ui.post(()->{
                // Ignore stale responses after switching feed/category.
                if(generation!=feedGeneration || !requestSearch.equals(onlineSearch)||!requestCategory.equals(activeCategory))return;
                loading=false;
                for(Post p:results)if(seenRemote.add(p.id))discovery.add(p);
                if(discovery.size()>100){
                    // Limit dynamic view count; full production app should use RecyclerView pagination.
                    while(discovery.size()>100)discovery.remove(0);
                }
                if(!error.isEmpty()){
                    categoryOffsets.put(category,Math.max(0,categoryOffsets.getOrDefault(category,20)-20));
                    if(feedStatus!=null)feedStatus.setText("Couldn't load new images ("+error+"). Check internet and tap Load more.");
                }else if(results.isEmpty() && (!activeCategory.equals("All")||!onlineSearch.isEmpty())){
                    hasMore=false;
                }
                renderFeed();
                if(!error.isEmpty()&&feedStatus!=null)feedStatus.setText("Connection problem. Check internet and tap Load more.");
            });
        });
    }
    private String plainMeta(JSONObject meta,String key){
        JSONObject j=meta.optJSONObject(key);if(j==null)return "";
        String value=android.text.Html.fromHtml(j.optString("value","").replaceAll("<[^>]*>"," "),android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim();
        return value.length()>120?value.substring(0,120)+"...":value;
    }
    private JSONObject fetchPixabayCached(String endpoint) throws Exception {
        File folder=new File(getCacheDir(),"pixabay_api");
        if(!folder.exists()&&!folder.mkdirs())throw new IOException("Cache directory failed");
        // Keyed by the entire URL including query parameters, while keeping credentials out of filenames.
        String fileKey=Integer.toHexString(endpoint.hashCode())+"_"+endpoint.length();
        File cached=new File(folder,fileKey+".json");
        long age=System.currentTimeMillis()-cached.lastModified();
        if(cached.exists()&&age>=0&&age<24L*60*60*1000){
            try(FileInputStream in=new FileInputStream(cached);ByteArrayOutputStream buf=new ByteArrayOutputStream()){
                byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)buf.write(b,0,n);
                return new JSONObject(buf.toString("UTF-8"));
            }catch(Exception ignored){} // Corrupt cache: fetch again.
        }
        HttpURLConnection c=(HttpURLConnection)new URL(endpoint).openConnection();
        c.setConnectTimeout(12000);c.setReadTimeout(12000);
        c.setRequestProperty("Accept","application/json");
        try{
            int code=c.getResponseCode();
            if(code!=200)throw new IOException("Pixabay HTTP "+code+" (check key or quota)");
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            try(InputStream in=c.getInputStream()){
                byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){
                    if(out.size()+n>2000000)throw new IOException("API response too large");
                    out.write(b,0,n);
                }
            }
            JSONObject data=new JSONObject(out.toString("UTF-8"));
            try(FileOutputStream stream=new FileOutputStream(cached)){stream.write(out.toByteArray());}
            return data;
        }finally{c.disconnect();}
    }
    private File downloadImage(String remoteUrl,String id) throws IOException {
        File folder=new File(getFilesDir(),"saved_pixabay");
        if(!folder.exists()&&!folder.mkdirs())throw new IOException("Could not create image directory");
        File target=new File(folder,id.replaceAll("[^a-zA-Z0-9_-]", "_")+".jpg");
        if(target.exists()&&target.length()>0)return target;
        HttpURLConnection c=(HttpURLConnection)new URL(remoteUrl).openConnection();
        c.setConnectTimeout(12000);c.setReadTimeout(12000);
        if(c.getResponseCode()!=200)throw new IOException("Image HTTP "+c.getResponseCode());
        File temp=new File(folder,target.getName()+".tmp");
        try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(temp)){
            byte[] buffer=new byte[8192];int count;long size=0;
            while((count=in.read(buffer))!=-1){size+=count;if(size>12000000)throw new IOException("Image too large");out.write(buffer,0,count);}
        }finally{c.disconnect();}
        if(!temp.renameTo(target))throw new IOException("Could not store image");
        return target;
    }
    private void loadRemoteImage(ImageView view,String url){
        Bitmap cached=imageCache.get(url);
        if(cached!=null){view.setImageBitmap(cached);return;}
        view.setImageDrawable(null);view.setBackgroundColor(0xffeeeeeb);
        view.setTag(url);
        io.execute(()->{
            try{
                HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
                c.setConnectTimeout(12000);c.setReadTimeout(12000);
                c.setRequestProperty("User-Agent","PinspireStudentProject/1.1 (Android)");
                Bitmap b;
                try(InputStream in=c.getInputStream()){
                    BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=2;
                    b=BitmapFactory.decodeStream(in,null,o);
                }finally{c.disconnect();}
                if(b!=null){imageCache.put(url,b);ui.post(()->{if(url.equals(view.getTag()))view.setImageBitmap(b);});}
            }catch(Exception ignored){}
        });
    }
    @Override public void onBackPressed(){if("home".equals(currentScreen))super.onBackPressed();else home();}
}
