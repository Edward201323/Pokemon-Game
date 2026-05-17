package Main;
import entity.Entity;
public class CollisionChecker {
    GamePanel gp;
    public CollisionChecker(GamePanel gp){
        this.gp = gp;
    }

    public void checkTile(Entity entity){
        int entityLeftWorldX = entity.worldX + entity.solidArea.x;
        int entityRightWorldX = entity.worldX + entity.solidArea.x + entity.solidArea.width;
        int entityTopWorldY = entity.worldY + entity.solidArea.y;
        int entityBottomWorldY = entity.worldY + entity.solidArea.y + entity.solidArea.height;

        int entityLeftCol = entityLeftWorldX/gp.tileSize;
        int entityRightCol = entityRightWorldX/gp.tileSize;
        int entityTopRow = entityTopWorldY/gp.tileSize;
        int entityBottomRow = entityBottomWorldY/gp.tileSize;

        int tileNum1, tileNum2;

        switch(entity.direction){
        case "up":
        entityTopRow = (entityTopWorldY - entity.speed)/gp.tileSize;
        tileNum1 = gp.tileM.mapTileNum[entityLeftCol][entityTopRow];
        tileNum2 = gp.tileM.mapTileNum[entityRightCol][entityTopRow];
        if(gp.tileM.tile[tileNum1].collision == true || gp.tileM.tile[tileNum2].collision == true){
            entity.collisionOn = true;
        }
        break;
        case "down":
        entityBottomRow = (entityBottomWorldY + entity.speed)/gp.tileSize;
        tileNum1 = gp.tileM.mapTileNum[entityLeftCol][entityBottomRow];
        tileNum2 = gp.tileM.mapTileNum[entityRightCol][entityBottomRow];
        if(gp.tileM.tile[tileNum1].collision == true || gp.tileM.tile[tileNum2].collision == true){
            entity.collisionOn = true;
        }
        break;
        case "left":
        entityLeftCol = (entityLeftWorldX - entity.speed)/gp.tileSize;
        tileNum1 = gp.tileM.mapTileNum[entityLeftCol][entityTopRow];
        tileNum2 = gp.tileM.mapTileNum[entityLeftCol][entityBottomRow];
        if(gp.tileM.tile[tileNum1].collision == true || gp.tileM.tile[tileNum2].collision == true){
            entity.collisionOn = true;
        }
        break;
        case"right":
        entityRightCol = (entityRightWorldX + entity.speed)/gp.tileSize;
        tileNum1 = gp.tileM.mapTileNum[entityRightCol][entityTopRow];
        tileNum2 = gp.tileM.mapTileNum[entityRightCol][entityBottomRow];
        if(gp.tileM.tile[tileNum1].collision == true || gp.tileM.tile[tileNum2].collision == true){
            entity.collisionOn = true;
        }
        break;
        }
    }

    public int checkObject(Entity entity, boolean player){
        //if player is hitting object, return index of the object
        int index = 999;

        for(int i = 0; i<gp.obj.size(); i++){
            if(gp.obj.get(i)!=null){
                //get entity's solid area position
                entity.solidArea.x = entity.worldX + entity.solidArea.x;
                entity.solidArea.y = entity.worldY + entity.solidArea.y;
                //get objects solid area position
                gp.obj.get(i).solidArea.x = gp.obj.get(i).worldX + gp.obj.get(i).solidArea.x;
                gp.obj.get(i).solidArea.y = gp.obj.get(i).worldY + gp.obj.get(i).solidArea.y;

                switch(entity.direction){
                    case "up":
                    entity.solidArea.y -= entity.speed;
                    if(entity.solidArea.intersects(gp.obj.get(i).solidArea)){
                        if(gp.obj.get(i).collision == true){
                            entity.collisionOn = true;
                        }
                        if(player == true){
                            index = i;
                        }
                    }
                    break;
                    case "down":
                    entity.solidArea.y +=entity.speed;
                    if(entity.solidArea.intersects(gp.obj.get(i).solidArea)){
                        if(gp.obj.get(i).collision == true){
                            entity.collisionOn = true;
                        }
                        if(player == true){
                            index = i;
                        }
                    }
                    break;
                    case "left":
                    entity.solidArea.x -= entity.speed;
                    if(entity.solidArea.intersects(gp.obj.get(i).solidArea)){
                        if(gp.obj.get(i).collision == true){
                            entity.collisionOn = true;
                        }
                        if(player == true){
                            index = i;
                        }
                    }
                    break;
                    case "right":
                    entity.solidArea.x += entity.speed;
                    if(entity.solidArea.intersects(gp.obj.get(i).solidArea)){
                        if(gp.obj.get(i).collision == true){
                            entity.collisionOn = true;
                        }
                        if(player == true){
                            index = i;
                        }
                    }
                }
                entity.solidArea.x = entity.solidAreaDefaultX;
                entity.solidArea.y = entity.solidAreaDefaultY;
                gp.obj.get(i).solidArea.x = gp.obj.get(i).solidAreaDefaultX;
                gp.obj.get(i).solidArea.y = gp.obj.get(i).solidAreaDefaultY;
            }
        }
        return index;
    }
}
