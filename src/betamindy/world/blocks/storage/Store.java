package betamindy.world.blocks.storage;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.input.KeyCode;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.*;
import betamindy.*;
import betamindy.content.*;
import betamindy.graphics.*;
import betamindy.type.*;
import betamindy.type.shop.*;
import betamindy.ui.*;
import betamindy.world.blocks.storage.AnucoinNode.*;
import mindustry.content.*;
import mindustry.entities.units.*;
import mindustry.graphics.*;
import mindustry.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.ui.dialogs.*;
import mindustry.world.*;
import mindustry.world.blocks.payloads.*;

import static arc.Core.atlas;
import static mindustry.Vars.*;
import static mindustry.Vars.world;

public class Store extends Block {
    public int defaultAnucoins = 500;

    public String[] categories;
    public PurchaseItem[] purchases;
    public boolean navigationBar = true;

    BaseDialog shopDialog;
    float[] scrollPos;
    /** [n] is the starting index of the categories[n + 1] in purchases. The first category always starts at index 0. */
    int[] catStarts;
    private static final Seq<PurchaseItem> tmps = new Seq<>();

    public Store(String name, Object... items){
        super(name);

        int n = items.length / 2;
        Log.info("n:"+n);
        tmps.clear();
        categories = new String[n];
        scrollPos = new float[n];
        catStarts = new int[n];
        for(int i = 0; i < n; i++){
            categories[i] = (String) items[i * 2];
            tmps.addAll((PurchaseItem[]) items[i * 2 + 1]);
            catStarts[i] = tmps.size;
        }
        purchases = tmps.toArray(PurchaseItem.class);
        update = solid = sync = configurable = true;
        saveConfig = false;
        unloadable = false;
    }

    /*
    @Override
    public TextureRegion[] icons(){
        return teamRegion.found() ? new TextureRegion[]{region, topRegion, teamRegions[Team.sharded.id], spinTeamRegions[Team.sharded.id], spinRegion} : new TextureRegion[]{region, topRegion};
    }
     */

    @Override
    public String emoji(){
        return AnucoinTex.emoji;
    }

    public class StoreBuild extends Building implements CoinBuild, BankLinked{
        public int anucoins = defaultAnucoins;
        private int anubank;
        float buttonWidth = 210f;
        public float scl;

        @Override
        public int coins(){
            return anucoins;
        }

        @Override
        public void handleCoin(Building source, int amount){
            anucoins += amount; //debts can be a thing here
        }

        @Override
        public int acceptCoin(Building source, int amount){
            return amount;
        }

        @Override
        public boolean outputCoin(){
            return anucoins > 0;
        }

        @Override
        public boolean occupied(Tile other){
            return getLink() != null && world.build(anubank) != other.build;
        }

        @Override
        public void setLink(Tile other){
            if(other.build == null) return;
            anubank = other.build.pos();
        }

        @Override
        public void removeLink(Tile other){
            if(world.build(anubank) == other.build) anubank = -1;
        }

        public int totalCoins(){
            AnucoinNodeBuild bank = getLink();
            if(bank == null) return anucoins;
            return Math.max(0, bank.coins()) + anucoins;
        }

        public void removeCoins(int a){
            anucoins -= a;
            if(anucoins < 0){
                AnucoinNodeBuild bank = getLink();
                if(bank != null){
                    bank.handleCoin(this, anucoins);
                    anucoins = 0;
                }
            }
        }

        public AnucoinNodeBuild getLink(){
            if(anubank == -1) return null;
            if(world.build(anubank) instanceof AnucoinNodeBuild bank) return bank;
            return null;
        }

        public void extraButton(Table pane, PurchaseItem item, int i){
            int price = item.cost;

            pane.button(t -> {
                t.left();
                item.buildButton(t);
            }, () -> {
                if(totalCoins() >= price){
                    configure(i);

                    if(item.abort) shopDialog.hide();
                }
            }).left().growX().disabled(b -> !item.unlocked.get(this));
            pane.row();
        }

        @Override
        public void configured(Unit builder, Object value){
            if(value instanceof Integer){
                int i = (Integer)value;
                if(purchases == null || i < 0 || i >= purchases.length) return;
                PurchaseItem item = purchases[i];
                if(totalCoins() >= item.cost){
                    if(item.purchase(this, builder)){
                        removeCoins(item.cost);
                    }
                }
            }
            else super.configured(builder, value);
        }

        @Override
        public boolean acceptPayload(Building source, Payload payload) {
            return false;
        }

        @Override
        public boolean acceptItem(Building source, Item item){
            return false;
        }

        @Override
        public void drawLight(){
            Drawf.light(team, x, y, lightRadius, Pal.accent, 0.65f + Mathf.absin(20f, 0.1f));
        }

        @Override
        public boolean shouldHideConfigure(Player player) {
            return true;
        }

        @Override
        public void drawConfigure() {}

        @Override
        public void buildConfiguration(Table table) {
            super.buildConfiguration(table);

            float width = Math.min(Core.graphics.getWidth(), Core.graphics.getHeight());

            shopDialog = new BaseDialog(Core.bundle.get("ui.shop.title"));
            shopDialog.center();

            shopDialog.row();

            shopDialog.table(t -> {
                t.center();
                Image image = t.image(AnucoinTex.uiCoin).size(30f).center().padRight(10f).get();
                image.clicked(() -> {
                    if(Core.input.keyDown(KeyCode.shiftLeft) || mobile){
                        if(Core.app.getClipboardText().equals(AnucoinTex.emoji)){
                            Core.app.setClipboardText(AnucoinTex.emojiBit);
                            Vars.ui.showInfoFade("[pink]"+Core.bundle.get("copied")+"[]");
                        }
                        else{
                            Core.app.setClipboardText(AnucoinTex.emoji);
                            Vars.ui.showInfoFade("@copied");
                        }
                    }
                });
                t.label(() -> {
                    int a = totalCoins();
                    return a + " " + (a > anucoins ? Core.bundle.get("ui.trans.linked") : "");
                }).padRight(10f).center();
            });

            shopDialog.row();
            //shopDialog.pane(tbl -> {
            ScrollPane itemPane = new ScrollPane(new Table(tbl -> {
                tbl.center();
                int cat = 0;
                tbl.table(marker -> {
                    marker.image().color(Pal2.coin).height(4f).growX();
                    marker.add(Core.bundle.get("purchase.category." + categories[0])).color(Pal2.coin).pad(3f);
                    marker.image().color(Pal2.coin).height(4f).growX();
                }).fillX().growX();
                tbl.row();
                for(int i = 0; i < purchases.length; i++){
                    if(i == catStarts[cat]){
                        //next category
                        cat++;
                        scrollPos[cat] = tbl.getPrefHeight();
                        int anuke = cat;
                        tbl.table(marker -> {
                            marker.image().color(Pal2.coin).height(4f).growX();
                            marker.add(Core.bundle.get("purchase.category." + categories[anuke], "@ui.extra")).color(Pal2.coin).pad(3f);
                            marker.image().color(Pal2.coin).height(4f).growX();
                        }).fillX().growX();
                        tbl.row();
                    }
                    extraButton(tbl, purchases[i], i);
                }
                //Log.info("scrollPos:" + scrollPos[0] + "," + scrollPos[1] + "," + scrollPos[2]);
            }));

            if(navigationBar){
                shopDialog.table(topbar -> {
                    topbar.left();
                    topbar.defaults().padRight(10f).size(130f, 40f).left();
                    for(int i = 0; i < categories.length; i++){
                        int ii = i;
                        topbar.button(Core.bundle.get("purchase.category." + categories[i], "@ui.extra"), () -> {
                            itemPane.setScrollY(scrollPos[ii]);
                        });
                    }
                }).center().width(width * 0.6f);
                shopDialog.row();
            }

            shopDialog.add(itemPane).center().width(width * 0.6f);
            shopDialog.row();
            shopDialog.table(t -> {
                if(Vars.mobile){
                    buttonWidth = (width / 2f) * 0.55f;
                }
                t.button("@back", Icon.left, shopDialog::hide).size(buttonWidth, 64f);
            });

            shopDialog.addCloseListener();
            shopDialog.show();
        }

        @Override
        public byte version(){
            return 1;
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.i(anucoins);
            write.i(anubank);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            anucoins = read.i();
            if(revision == 1) anubank = read.i();
        }
    }
}