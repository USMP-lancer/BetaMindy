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
import betamindy.world.blocks.payloads.*;
import mindustry.content.*;
import mindustry.entities.units.*;
import mindustry.graphics.*;
import mindustry.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;
import mindustry.world.*;
import mindustry.world.blocks.payloads.*;
import mindustry.world.blocks.production.*;

import static arc.Core.atlas;
import static mindustry.Vars.*;

public class Shop extends PayloadAcceptor {
    public int defaultAnucoins = 500;
    public TextureRegion spinRegion;
    public TextureRegion[] spinTeamRegions;
    public float spinSpeed = 0.2f;
    public float spinShadowRadius = 15f;

    public @Nullable PurchaseItem[] purchases;
    public @Nullable Block[] soldBlocks;
    public boolean sellAllItems = false;
    public boolean sellAllUnits = false;
    public boolean sellAllBlocks = false;
    public boolean navigationBar = false;

    OrderedMap<Item, Float> itemScores;
    OrderedMap<UnitType, Float> unitScores;
    OrderedMap<UnitType, Integer> unitTypeMap = new OrderedMap<>();
    BaseDialog shopDialog;
    float[] scrollPos = {0, 0, 0, 0};

    public Shop(String name){
        super(name);

        update = solid = hasItems = outputsPayload = sync = rotate = configurable = true;
        saveConfig = false;
        acceptsItems = true;
        unloadable = false;

        config(Item.class, (ShopBuild tile, Item item) -> {
            int price = Math.max(Math.round(itemScores.get(item)), 15);
            if(tile.anucoins >= price){
                if(tile.addItemPayload(item, 15)){
                    tile.anucoins -= price;
                    tile.payVector.setZero();
                    tile.payRotation = tile.rotdeg();
                }
            }
        });
        config(UnitType.class, (ShopBuild tile, UnitType unit) -> {
            int price = Math.max(Math.round(unitScores.get(unit)), 15);
            if(tile.anucoins >= price && tile.payload == null) {
                tile.anucoins -= price;
                tile.payload = new UnitPayload(unit.create(tile.team));

                tile.payVector.setZero();
                tile.payRotation = tile.rotdeg();
            }
        });
        config(Block.class, (ShopBuild tile, Block block) -> {
            int price = BlockItem.getScore(block);
            if(tile.anucoins >= price && tile.payload == null) {
                tile.anucoins -= price;
                tile.payload = new BuildPayload(block, tile.team);

                tile.payVector.setZero();
                tile.payRotation = tile.rotdeg();
            }
        });
    }

    public int checkUnitType(UnitType unit){
        if(unit.flying) return 1;

        Unit u = unit.constructor.get();

        boolean water = u instanceof WaterMovec;

        return water ? 3 : 2;
    }

    public String colorToHex(Color color){
        return Integer.toHexString((int)(color.r * 255f)) + Integer.toHexString((int)(color.g * 255f)) + Integer.toHexString((int)(color.b * 255));
    }

    @Override
    public void createIcons(MultiPacker packer){
        Drawm.customTeamRegion(packer, name + "-spin");
        Drawm.generateTeamRegion(packer, this);
        super.createIcons(packer);
    }

    @Override
    public void init(){
        super.init();

        Runnable ee = () -> {
            itemScores = BetaMindy.itemScores;
            unitScores = BetaMindy.unitScores;

            for (UnitType unit : Vars.content.units()) {
                if (unitScores.containsKey(unit)) {
                    unitTypeMap.put(unit, checkUnitType(unit));
                }
            }
        };

        Events.on(EventType.ClientLoadEvent.class, e -> ee.run());

        Events.on(EventType.ServerLoadEvent.class, e -> ee.run());
    }

    @Override
    public void load() {
        super.load();

        spinRegion = atlas.find(name + "-spin");
        spinTeamRegions = Drawm.loadCustomTeamRegion(name + "-spin");
    }

    @Override
    public TextureRegion[] icons(){
        return teamRegion.found() ? new TextureRegion[]{region, topRegion, teamRegions[Team.sharded.id], spinTeamRegions[Team.sharded.id], spinRegion} : new TextureRegion[]{region, topRegion};
    }

    @Override
    public void drawRequestRegion(BuildPlan req, Eachable<BuildPlan> list){
        Draw.rect(region, req.drawx(), req.drawy());
        Draw.rect(outRegion, req.drawx(), req.drawy(), req.rotation * 90);
        Draw.rect(topRegion, req.drawx(), req.drawy());

        if(req.worldContext && Vars.player != null && teamRegion != null && teamRegion.found()){
            int tid = Vars.player.team().id;
            if(teamRegions[tid] == teamRegion) Draw.color(Vars.player.team().color);
            Draw.rect(teamRegions[tid], req.drawx(), req.drawy());
            Draw.rect(spinTeamRegions[tid], req.drawx(), req.drawy());
            Draw.rect(spinRegion, req.drawx(), req.drawy());
            Draw.color();
        }
        else if(teamRegion != null && teamRegion.found()){
            Draw.rect(teamRegions[Team.sharded.id], req.drawx(), req.drawy());
            Draw.rect(spinTeamRegions[Team.sharded.id], req.drawx(), req.drawy());
            Draw.rect(spinRegion, req.drawx(), req.drawy());
        }
    }

    @Override
    public String emoji(){
        return AnucoinTex.emoji;
    }

    public class ShopBuild extends PayloadAcceptor.PayloadAcceptorBuild<Payload> implements CoinBuild, BankLinked{
        public int anucoins = defaultAnucoins;
        private int anubank = -1; //default is -1
        float buttonWidth = 210f;
        public UnitType unit;
        public float scl;

        String airColor = colorToHex(StatusEffects.shocked.color);
        String groundColor = colorToHex(StatusEffects.melting.color);
        String navalColor = colorToHex(StatusEffects.wet.color);

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
            return anubank != -1 && world.build(anubank) != other.build;
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
        //todo totalCoins & removeCoins

        public boolean addItemPayload(Item item, int amount){
            if(payload == null){
                payload = new BuildPayload(MindyBlocks.box, team);
            }

            if(payload instanceof BuildPayload bp){
                if(bp.build.items == null) return false;
                bp.build.items.add(item, amount);

                return true;
            }

            return false;
        }

        public boolean addLiquidPayload(Liquid liquid, float amount){
            if(payload == null){
                payload = new BuildPayload(MindyBlocks.box, team);
            }

            if(payload instanceof BuildPayload bp){
                if(bp.build.liquids == null || (bp.build.liquids.currentAmount() > 0.2f && bp.build.liquids.current() != liquid)) return false;
                bp.build.liquids.add(liquid, amount);

                return true;
            }

            return false;
        }

        public boolean disabledBox(){
            return payload != null && (!(payload instanceof BuildPayload bp) || bp.build.items == null);
        }

        public boolean disabledLiquid(Liquid liquid){
            if(payload == null) return false;

            if(payload instanceof BuildPayload bp){
                return bp.build.liquids == null || ((bp.build.liquids.currentAmount() > 0.2f) && bp.build.liquids.current() != liquid);
            }
            return true;
        }

        public void itemButton(Table pane, Item item){
            int price = Math.max(Math.round(itemScores.get(item)), 15);

            pane.button(t -> {
                t.left();
                t.image(item.icon(Cicon.medium)).size(40).padRight(10f);

                t.table(tt -> {
                    tt.left();
                    String color = colorToHex(item.color);
                    tt.add("[#" + color + "]" + item.localizedName + "[] [accent]x15[]").growX().left();
                    tt.row();
                    tt.add(Core.bundle.get("ui.price") + ": " + Core.bundle.format("ui.anucoin.emoji", price)).left();;
                }).growX();
            }, () -> {
                if(anucoins >= price){
                    configure(item);
                }
            }).left().growX().disabled(b -> disabledBox());
            pane.row();
        }

        public void unitButton(Table pane, UnitType unit){
            int price = Math.max(Math.round(unitScores.get(unit)), 15);

            pane.button(t -> {
                t.left();

                t.image(unit.icon(Cicon.medium)).size(40).padRight(10f);

                t.table(tt -> {
                    int type = unitTypeMap.get(unit);
                    tt.left();

                    tt.add(unit.localizedName).growX().left().color(player == null || player.team() == null || player.team().id == Team.derelict.id ? Pal.accent : player.team().color);
                    tt.row();

                    tt.add(Core.bundle.get("ui.type") + ": " + (type == 1 ? "[#" + airColor + "]" + Core.bundle.get("ui.air") : (type == 2 ? "[#" + groundColor + "]" + Core.bundle.get("ui.ground") : "[#" + navalColor + "]" + Core.bundle.get("ui.naval")))).left();
                    tt.row();

                    tt.add(Core.bundle.get("ui.price") + ": " + Core.bundle.format("ui.anucoin.emoji", price)).left();;
                }).growX();
            }, () -> {
                if(anucoins >= price && payload == null) {
                    configure(unit);
                }
            }).left().growX().disabled(b -> payload != null);
            pane.row();
        }

        public void extraButton(Table pane, PurchaseItem item, int i){
            int price = item.cost;

            pane.button(t -> {
                t.left();
                item.buildButton(t);
            }, () -> {
                if(anucoins >= price){
                    configure(i);

                    if(item.abort) shopDialog.hide();
                }
            }).left().growX().disabled(b -> !item.unlocked.get(this));
            pane.row();
        }

        public void blockButton(Table pane, Block block){
            int price = BlockItem.getScore(block);

            pane.button(t -> {
                t.left();

                t.image(block.icon(Cicon.medium)).size(40).padRight(10f);

                t.table(tt -> {
                    tt.left();

                    tt.add(block.localizedName).growX().left().color(BlockItem.blockColor(block));
                    tt.row();

                    tt.add(Core.bundle.get("ui.price") + ": " + Core.bundle.format("ui.anucoin.emoji", price)).left();;
                }).growX();
            }, () -> {
                if(anucoins >= price && payload == null) {
                    configure(block);
                }
            }).left().growX().disabled(b -> payload != null);
            pane.row();
        }

        @Override
        public void configured(Unit builder, Object value){
            if(value instanceof Integer){
                int i = (Integer)value;
                if(purchases == null || i < 0 || i >= purchases.length) return;
                PurchaseItem item = purchases[i];
                if(anucoins >= item.cost){
                    if(item instanceof ShopItem shopitem){
                        if(shopitem.shop(this)){
                            anucoins -= item.cost;
                            payVector.setZero();
                            payRotation = rotdeg();
                        }
                    }else{
                        if(item.purchase(this, builder)){
                            anucoins -= item.cost;
                        }
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
        public void updateTile(){
            super.updateTile();
            moveOutPayload();
        }

        @Override
        public void dumpPayload(){
            super.dumpPayload();
            if(payload != null && payload instanceof BuildPayload bp){
                int off = size / 2 + 1;
                Tile other = tile.nearby(Geometry.d4x(rotation) * off, Geometry.d4y(rotation) * off);
                Log.info(other);
                if(other == null || !RBuild.validPlace(bp.block(), other.x, other.y, true)) return;
                //place da box
                bp.place(other, rotation);
                payload = null;
            }
        }

        @Override
        public void draw(){
            Draw.rect(region, x, y);
            Draw.rect(outRegion, x, y, rotdeg());

            drawPayload();
            Draw.z(Layer.blockOver + 0.001f);
            Draw.rect(topRegion, x, y);
            drawTeamTop();
            Draw.reset();
        }

        @Override
        public void drawLight(){
            Drawf.light(team, x, y, lightRadius, Pal.accent, 0.65f + Mathf.absin(20f, 0.1f));
        }

        @Override
        public void drawTeamTop(){
            if(block.teamRegion.found()){
                float r = Time.time * spinSpeed + id * 17f;

                Draw.z(Layer.blockOver + 0.0011f);
                if(block.teamRegions[team.id] == block.teamRegion) Draw.color(team.color);
                Draw.rect(block.teamRegions[team.id], x, y);
                Draw.z(Layer.blockOver + 0.002f);
                Drawm.spinSprite(spinTeamRegions[team.id], x, y, r);

                Draw.color();
                Draw.rect(spinRegion, x, y, r);
                Draw.z(Layer.blockOver + 0.0015f);
                Drawf.shadow(x, y, spinShadowRadius);
            }

            carried = false; //why, Anuke?
        }

        @Override
        public boolean shouldHideConfigure(Player player) {
            return true;
        }

        @Override
        public void drawConfigure() {}

        public void buildSellDialog(int[] price, Seq<ItemStack> items, Runnable confirmed){
            String text1 = Core.bundle.get("ui.sellAccept") + ":";
            String text2 = Core.bundle.get("ui.sellAccept2") + " [accent]" + price[0] + " " + Core.bundle.get("ui.anucoin.multiple") + "[]";
            BaseDialog dialog = new BaseDialog(Core.bundle.get("ui.shop.title"));
            dialog.cont.add(text1).width(mobile ? 400f : 500f).wrap().pad(4f).get().setAlignment(Align.center, Align.center);
            dialog.cont.row();
            dialog.cont.pane(p -> {
                for(ItemStack stack : items){
                    p.left();
                    p.table(t -> {
                        t.left();
                        t.table(tt -> {
                            tt.left();
                            tt.image(stack.item.icon(Cicon.medium)).left();
                            tt.add("x" + stack.amount).left();
                        }).growX().left();

                        t.add(" [accent]" + (int)Math.max((itemScores.get(stack.item) * stack.amount) / 30f, Math.max(stack.amount / 2f, 1)) + "[]").padRight(5f).left();

                        t.image(AnucoinTex.uiCoin).left();
                    }).left().growX();
                    p.row();
                }
            }).height(mobile ? 200f : 250f).width(mobile ? 400f : 500f);
            dialog.cont.row();
            dialog.cont.add(text2).width(mobile ? 400f : 500f).wrap().pad(4f).get().setAlignment(Align.center, Align.center);
            dialog.buttons.defaults().size(200f, 54f).pad(2f);
            dialog.setFillParent(false);
            dialog.buttons.button("@cancel", dialog::hide);
            dialog.buttons.button("@ok", () -> {
                dialog.hide();
                confirmed.run();
            });
            dialog.keyDown(KeyCode.enter, () -> {
                dialog.hide();
                confirmed.run();
            });
            dialog.keyDown(KeyCode.escape, dialog::hide);
            dialog.keyDown(KeyCode.back, dialog::hide);
            dialog.show();
        }

        @Override
        public void buildConfiguration(Table table) {
            super.buildConfiguration(table);
            
            float width = Math.min(Core.graphics.getWidth(), Core.graphics.getHeight());
            float height = Math.max(Core.graphics.getWidth(), Core.graphics.getHeight());

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
                t.label(() -> String.valueOf(anucoins)).padRight(10f).center();
            });

            shopDialog.row();
            //shopDialog.pane(tbl -> {
            ScrollPane itemPane = new ScrollPane(new Table(tbl -> {
                tbl.center();
                if(purchases != null) {
                    tbl.table(marker -> {
                        marker.image().color(Pal2.coin).height(4f).growX();
                        marker.add(Core.bundle.get("ui.extra")).color(Pal2.coin).pad(3f);
                        marker.image().color(Pal2.coin).height(4f).growX();
                    }).fillX().growX();
                    tbl.row();

                    for(int i = 0; i < purchases.length; i++){
                        extraButton(tbl, purchases[i], i);
                    }
                }
                if(sellAllItems){
                    scrollPos[1] = tbl.getPrefHeight();
                    tbl.table(marker -> {
                        marker.image().color(Pal2.coin).height(4f).growX();
                        marker.add(Core.bundle.get("content.item.name")).color(Pal2.coin).pad(3f);
                        marker.image().color(Pal2.coin).height(4f).growX();
                    }).fillX().growX();
                    tbl.row();

                    for(Item item : Vars.content.items()) {
                        if(item == MindyItems.bittrium) continue;
                        if(itemScores.containsKey(item)) {
                            itemButton(tbl, item);
                        }
                    }
                }
                if(sellAllUnits){
                    scrollPos[2] = tbl.getPrefHeight();
                    tbl.table(marker -> {
                        marker.image().color(Pal2.coin).height(4f).growX();
                        marker.add(Core.bundle.get("content.unit.name")).color(Pal2.coin).pad(3f);
                        marker.image().color(Pal2.coin).height(4f).growX();
                    }).fillX().growX();
                    tbl.row();

                    for(UnitType unit : Vars.content.units()) {
                        if(unitScores.containsKey(unit)) {
                            unitButton(tbl, unit);
                        }
                    }
                }

                if(sellAllBlocks || soldBlocks != null){
                    scrollPos[3] = tbl.getPrefHeight();
                    tbl.table(marker -> {
                        marker.image().color(Pal2.coin).height(4f).growX();
                        marker.add(Core.bundle.get("content.block.name")).color(Pal2.coin).pad(3f);
                        marker.image().color(Pal2.coin).height(4f).growX();
                    }).fillX().growX();
                    tbl.row();

                    if(sellAllBlocks){
                        for(Block block : Vars.content.blocks()) {
                            if(!block.isHidden() && block.requirements.length > 0){
                                blockButton(tbl, block);
                            }
                        }
                    }
                    else{
                        for(Block block : soldBlocks) {
                            if(!block.isHidden() && block.requirements.length > 0){
                                blockButton(tbl, block);
                            }
                        }
                    }
                }
                //Log.info("scrollPos:" + scrollPos[0] + "," + scrollPos[1] + "," + scrollPos[2]);
            }));

            if(navigationBar){
                shopDialog.table(topbar -> {
                    topbar.left();
                    topbar.defaults().padRight(10f).size(110f, 40f).left();
                    if(purchases != null){
                        topbar.button("@ui.extra", () -> {
                            itemPane.setScrollY(scrollPos[0]);
                        });
                    }
                    if(sellAllItems){
                        topbar.button("@content.item.name", () -> {
                            itemPane.setScrollY(scrollPos[1]);
                        });
                    }
                    if(sellAllUnits){
                        topbar.button("@content.unit.name", () -> {
                            itemPane.setScrollY(scrollPos[2]);
                        });
                    }
                    if(sellAllBlocks || soldBlocks != null){
                        topbar.button("@content.block.name", () -> {
                            itemPane.setScrollY(scrollPos[3]);
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
                /*
                t.button(Core.bundle.get("ui.sell"), Icon.add, () -> {
                    int[] price = new int[]{0};
                    Seq<ItemStack> itemStack = new Seq<>();

                    items.each((Item ii, int aa) -> {
                        if(!itemScores.containsKey(ii)) return;

                        price[0] += (int)Math.max((itemScores.get(ii) * aa) / 30f, Math.max(aa / 2f, 1));

                        itemStack.add(new ItemStack().set(ii, aa));
                    });

                    buildSellDialog(price, itemStack, () -> {
                        for(ItemStack stack : itemStack){
                            items.remove(stack.item, stack.amount);
                        }

                        anucoins += price[0];
                        updateAnucoins();
                    });
                }).size(buttonWidth, 64f).disabled(e -> items.total() == 0);
                */
            });

            shopDialog.addCloseListener();
            shopDialog.show();
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.i(anucoins);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            anucoins = read.i();
        }
    }
}