package com.gascan.archiver;
import com.mpatric.mp3agic.ID3v1;
import com.mpatric.mp3agic.Mp3File;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;

import javax.servlet.http.*;
import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.sql.*;
import java.util.ArrayList;


public class Main {
    public static void main(String[] args) throws Exception{
        Path path;
        ArrayList<Song> songs = new ArrayList<>();
        ArrayList<Mp3File> mp3s = new ArrayList<>();
        ArrayList<ID3v1> tags = new ArrayList<>();

        args = new String[1];
        args[0] = "C:\\Projects\\Java\\mp3-archiver\\out\\artifacts\\mp3_archiver";

        if(args.length > 0){
            path = Paths.get(args[0]);
        }
        else
        {
            throw new IllegalArgumentException("Path is empty.");
        }
        mp3s = getMp3s(path);
        tags = getId1Tags(mp3s);
        songs = getSongs(tags);
        insertSongsIntoDatabase(songs);
        startWebServer();


    }
    //Read all .MP3's from Directory and return arraylist of these mp3files.
    public static ArrayList<Mp3File> getMp3s(Path path)
    {
        ArrayList<Mp3File> mp3Files = new ArrayList<>();
        ArrayList<Path> paths = new ArrayList<>();
        try (DirectoryStream<Path> stream =
                     Files.newDirectoryStream(path, "*.{mp3}")) {
            for (Path entry: stream) {
                paths.add(Paths.get(entry.getFileName().toUri()));
            }
        } catch (IOException x) {
            // IOException can never be thrown by the iteration.
            // In this snippet, it can // only be thrown by newDirectoryStream.
            System.err.println(x);
        }
        for(Path p : paths){
            try{
                mp3Files.add(new Mp3File(p));
            }
            catch(Exception e){
                System.out.println(e);
            };
        }


        return mp3Files;
    }
    //Create arraylist of ID3v1 (containing info about artist, album, etc..) and return arraylist of these.
    public static ArrayList<ID3v1> getId1Tags(ArrayList<Mp3File> mp3Files){
        ArrayList<ID3v1> ID3v1s = new ArrayList<>();
        for(Mp3File mp3File : mp3Files){
            if(mp3File.hasId3v1Tag()){
                ID3v1 tag = mp3File.getId3v1Tag();
                ID3v1s.add(tag);
            }
        }
        return ID3v1s;
    }
    //Create song-objects from ID3v1tags, add them to list and return arraylist<Song>;
    public static ArrayList<Song> getSongs(ArrayList<ID3v1> tags){
        ArrayList<Song> songs = new ArrayList<>();
        for (ID3v1 t : tags) {
            songs.add(new Song(t.getArtist(), t.getYear(), t.getAlbum(), t.getTitle()));
        }
        return songs;
    }
    //Takes ArrayList<Song> and inserts them into H2 database.
    public static void insertSongsIntoDatabase(ArrayList<Song> songs) throws  SQLException{
        try (Connection conn = DriverManager.getConnection("jdbc:h2:~/mydatabase;AUTO_SERVER=TRUE;INIT=runscript from './create.sql'")) {
            PreparedStatement delSt = conn.prepareStatement("DELETE FROM SONGS"); //Deletes all data in table before inserting the songs.
            delSt.execute();
            PreparedStatement st = conn.prepareStatement("insert into SONGS (artist, year, album, title) values (?, ?, ?, ?);");

            for (Song song : songs) {
                st.setString(1, song.getArtist());
                st.setString(2, song.getYear());
                st.setString(3, song.getAlbum());
                st.setString(4, song.getTitle());
                st.addBatch();
            }

            int[] updates = st.executeBatch();
            System.out.println("Inserted [=" + updates.length + "] records into the database");
        }
    }
    //Starts a simple Jetty webserver.
    public static void startWebServer() throws Exception{
        Server server = new Server(8080);

        ServletContextHandler context = new ServletContextHandler(
                ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        context.setResourceBase(System.getProperty("java.io.tmpdir"));
        server.setHandler(context);

        context.addServlet(SongServlet.class, "/songs");
        server.start();

        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browse(new URI("http://localhost:8080/songs")); //Opens browser that surfs to the site
        }
    }
    //Servlet for Jetty webserver.
    public static class SongServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            StringBuilder builder = new StringBuilder();
            try (Connection conn = DriverManager.getConnection("jdbc:h2:~/mydatabase")) {

                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("select * from SONGS");

                while (rs.next()) {
                    builder.append("<tr class=\"table\">")
                            .append("<td>").append(rs.getString("year")).append("</td>")
                            .append("<td>").append(rs.getString("artist")).append("</td>")
                            .append("<td>").append(rs.getString("album")).append("</td>")
                            .append("<td>").append(rs.getString("title")).append("</td>")
                            .append("</tr>");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }

            String string = "<html><h1>Your Songs</h1><table><tr><th>Year</th><th>Artist</th><th>Album</th><th>Title</th></tr>" + builder.toString() + "</table></html>";
            resp.getWriter().write(string);
        }
    }

}
