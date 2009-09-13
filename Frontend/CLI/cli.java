package Branch.Shokora.Frontend.CLI;

import java.util.*;
import Branch.Shokora.Backend.XML.*;
import Branch.Shokora.Backend.*;
import java.net.*;
import jcifs.smb.*;


/**
 *©Shokora 2009
 * @author shokora
 * @todo make a standardized parameter scan function
 */
public class cli
{
    private ArrayList<Command> commandList;
    private ArrayList<SearchResult> onlineResults;
    private ArrayList<String> fileList;
    private SDirectory currentDir;
    private Scanner in;

    public static void main(String[] args)
    {
        new cli();
    }

    public cli()
    {
        commandList = new ArrayList<Command>();
        in = new Scanner(System.in);

        //add commands to the commandlist
        commandList.add(new Search("search",-1,"Search on CampusSearch for the specified information"));
        commandList.add(new Get("get",-1,"Get a file from the search results"));
        commandList.add(new Open("open",-1,"Open a network resource"));
        commandList.add(new ChangeDirectory("cd",1,"Change the current directory"));
        commandList.add(new List("ls",0,"Get the file list from a directory"));
        commandList.add(new Help("help",0,"Print this menu"));
        commandList.add(new Exit("exit",0,"Exit this application"));

        printMenu();

        while(true)
        {
            runCommand(readCommand());
        }
    }

    /**
     * Read a line from the commandline
     * @return the line of input
     */
    public String readLine()
    {
        String current = "";

        if(currentDir != null) current = currentDir.getCutPath();

        System.out.print("Command:"+current+"$ ");System.out.flush();
        
        if(in.hasNextLine())
        {
            return in.nextLine();
        }

        return "";
    }

    /**
     * Make sure the command isn't empty
     * @return
     */
    public Scanner readCommand()
    {
        String command="";

        while(command.equals(""))
        {
            command = readLine();
        }

        return new Scanner(command);
    }

    /**
     * Compare the String command and the amount of parameters with the installed commands
     * @param scan
     */
    public void runCommand(Scanner scan)
    {
        ArrayList<String> pars = new ArrayList<String>();
        String token = "";

        int i = 0;
        while(scan.hasNext())
        {
            if(i == 0)
            {
                token = scan.next(); //The token is the first word
                i++;
            }
            else pars.add(scan.next());
        }

        for(Command command : commandList)
        {
            if(command.validizeCall(token, pars))
            {
                command.run(pars);
                return;
            }
        }

        System.out.println("Error: command not recognized");
    }

    /**
     * Print the menu you see at the startup of the app
     */
    public void printMenu()
    {
        System.out.println("Prometheus dev 0.01");

        for(Command command : commandList)
        {
            System.out.println(String.valueOf(command.getToken()+" "+command.toString()));
        }
    }

    /**
     * Make one string out of an ArrayList of strings
     * @param stringList
     * @return
     */
    public String makeString(ArrayList<String> stringList, String connector)
    {
        String endString = "";

        for(String substring : stringList)
        {
            if(!substring.equals(""))
            {
                endString += substring+connector;
            }
        }

        return endString.substring(0,endString.length()-1); //Else there will be a connector at the end with no connection
    }

    /**
     * Every directory path should end with a /
     * @param directory
     * @return
     */
    public String validizeDirectory(String directory)
    {
        if(directory.charAt(directory.length()-1) != '/')
        {
            directory += "/";
        }

        return directory;
    }

    /**
     * Searches on campussearch for a file and prints the results with a number next to it.
     * This is done via XML.
     * Flags:
     * -page int The page that you want :  it's (page+1)*29 so page 0 == 0 - 29
     * -minsize String minsize of the files : 1G or 1MB
     * -maxsize String maxsize fo the files : 2G or 2MB
     * -dirsonly bool will show only dirs
     */
    private class Search extends Command
    {
        public Search(String token, int argCount, String description)
        {
            super(token,argCount,description);
        }

        public void run(ArrayList<String> args)
        {
            onlineResults = new ArrayList<SearchResult>();
            String queryFlags = "";


            //Check for flags, see the javadoc for meaning of the flags
            for(int i=0;i<args.size();i++)
            {
                String arg = args.get(i);

                if(arg.equals("-page"))
                {
                    try
                    {
                        queryFlags += "&page="+Integer.valueOf(args.get(i+1));
                        args.set(i, "");
                        args.set(++i, "");
                    }
                    catch(NumberFormatException e)
                    {
                        System.out.println("Error: the page has to be an integer");
                        return;
                    }
                }
                else if(arg.equals("-minsize"))
                {
                    queryFlags += "&minsize="+args.get(i+1);
                    args.set(i, "");
                    args.set(++i, "");
                }
                else if(arg.equals("-maxsize"))
                {
                    queryFlags += "&maxsize="+args.get(i+1);
                    args.set(i, "");
                    args.set(++i, "");
                }
                else if(arg.equals("-dirsonly"))
                {
                    queryFlags += "&dirsonly=yes";
                    args.set(i, "");
                }
            }

            String query = makeString(args,"+");
            query = "http://search.student.utwente.nl/api/search?q="+query+queryFlags;
            
            try
            {
                System.out.println(query);
                URL url = new URL(query);
                HttpURLConnection conn = (HttpURLConnection)url.openConnection();

                ParserCampusSearch xml = new ParserCampusSearch(conn.getInputStream());
                ArrayList<SearchResult> results = xml.getSearchResults();

                int i = 0;
                for(SearchResult result : results)
                {
                    if(result.getOnline())
                    {
                        System.out.println(i++ + " Path: "+result.getFullPath()+" Size: "+result.getSizeReadable());
                        onlineResults.add(result);
                    }
                }
            }
            catch(Exception e)
            {
                System.out.println("Error: no results for this search query");
            }
        }
    }

    /**
     * One of the most important features, the downloading. It aims to be very flexible, there
     * are currently 4 ways of downloading:
     * - With a number from the search results
     * - With a number from the file listing
     * - With the literal filename from the current directory
     * - With a full url
     *
     * It's a bit messy i'm probably going to rewrite it in a later version
     *
     */
    private class Get extends Command
    {
        public Get(String token, int argCount, String description)
        {
            super(token,argCount,description);
        }

        public void run(ArrayList<String> args)
        {
            if(args.size() == 0)
            {
                System.out.println("Error: you have to give some argument to get");
                return;
            }

            String call = makeString(args," ");
            Scanner intScan = new Scanner(call);
            int number = -1;

            //This is very ugly i will probably make it better later...
            while(intScan.hasNext())
            {
                if(intScan.hasNextInt()) number = intScan.nextInt();
                else intScan.next();
            }

            SmbFile downloadFile = null;

            //Download a file from the search results with a number indicator
            if(onlineResults != null && call.contains("-s"))
            {
                try
                {
                    SearchResult result = onlineResults.get(number);

                    downloadFile = new SmbFile(result.getFullPath());
                }
                catch(NullPointerException e)
                {
                    System.out.println("Error: first char needs to be the number of a download");
                }
                catch(Exception e)
                {
                    System.out.println(e.getMessage());
                    System.out.println("Error: could not connect to the host");
                }
            }
            else if(onlineResults == null && call.contains("-s")) //You can't download search results if there are none
            {
                System.out.println("Error: you have to search something before you can download it");
            }
            else if(fileList != null) //Download from a directory with a number indicator
            {
                try
                {
                    String result = fileList.get(number);
                    downloadFile = new SmbFile(result);
                }
                catch(NullPointerException e)
                {
                    if(args.get(0).substring(0,6).equals("smb://")) //See if it's a full url
                    {
                        try
                        {
                            downloadFile = new SmbFile(args.get(0));
                        }
                        catch(Exception ex2)
                        {
                            System.out.println("Error: "+ex2.getMessage());
                        }
                    }
                    else //If it's not a full url it's probably the name of a file in the current dir
                    {
                        try
                        {
                            downloadFile = new SmbFile(currentDir+args.get(0));
                        }
                        catch(Exception ex3)
                        {
                            System.out.println("Error: "+ex3.getMessage());
                        }
                    }
                }
                catch(MalformedURLException ex1)
                {
                    System.out.println("Error: "+ex1.getMessage());
                }
            }

            try
            {
                if(downloadFile != null && downloadFile.isFile())
                {

                   System.out.println("Getting file: "+downloadFile.getName());
                   new SFile(downloadFile).get();
                   System.out.println("Download is done");

                }
                else if(downloadFile != null && downloadFile.isDirectory() && call.contains("-r"))
                {
                    new SDirectory(downloadFile).get(true);
                }
                else if(downloadFile != null && downloadFile.isDirectory())
                {
                    new SDirectory(downloadFile).get(false);
                }
            }
            catch(Exception e)
            {
                System.out.println("Error: "+e.getMessage());
            }
        }
    }

    /**
     * Print the menu, will be changed in a later version as a 'per command help feature'
     */
    private class Help extends Command
    {
        public Help(String token, int argCount, String description)
        {
            super(token,argCount,description);
        }

        public void run(ArrayList<String> args)
        {
            printMenu();
        }
    }

    /**
     * Exit the application
     */
    private class Exit extends Command
    {
        public Exit(String token, int argCount, String description)
        {
            super(token,argCount,description);
        }

        public void run(ArrayList<String> args)
        {
            System.exit(0);
        }
    }

    /**
     * Change the directory either by using a literal directory name or a number from
     * the filelist.
     */
    private class ChangeDirectory extends Command
    {
        public ChangeDirectory(String token, int argCount, String description)
        {
            super(token,argCount,description);
        }

        public void run(ArrayList<String> args)
        {
            try
            {
                if(currentDir != null)
                {
                    if(args.get(0).equals(".."))
                    {
                        currentDir = new SDirectory(currentDir.getSMBFile().getParent());
                        fileList = currentDir.listFiles(false, false); //update the filelist or shit will fuck up
                    }
                    else
                    {
                        String directory;

                        try
                        {
                            directory = fileList.get(Integer.valueOf(args.get(0)));
                        }
                        catch(NumberFormatException e)
                        {
                            args.set(0,validizeDirectory(args.get(0)));
                            directory = currentDir.getSMBFile().getPath()+args.get(0);
                        }

                        try
                        {
                            currentDir = new SDirectory(directory);
                        }
                        catch(Exception e)
                        {
                            System.out.println("Error: "+e.getMessage());
                        }

                        fileList = currentDir.listFiles(false, false); //update the filelist or shit will fuck up
                    }
                        
                }
                else
                {
                    System.out.println("Error: you have to open a resource before you can change directory");
                }
            }
            catch(Exception e)
            {
                System.out.println("Error: "+e.getMessage());
            }
        }
    }
    
    /**
     * Open a SMB repository
     * -s for opening a directory in a search result
     */
    private class Open extends Command
    {
        public Open(String token, int argCount, String description)
        {
            super(token,argCount,description);
        }

        public void run(ArrayList<String> args)
        {
            String openDir = "";
            if(makeString(args," ").contains("-s") && onlineResults != null)
            {
                try
                {
                    openDir = validizeDirectory(onlineResults.get(Integer.valueOf(args.get(1))).getFullPath());
                }
                catch(NumberFormatException e)
                {
                    System.out.println("Error: "+e.getMessage());
                }
            }
            else
            {
                openDir = validizeDirectory(args.get(0));
            }

            if(!openDir.equals(""))
            {
                try
                {
                    currentDir = new SDirectory(openDir);
                }
                catch(Exception e)
                {
                    System.out.println("Error: "+e.getMessage());
                }
            }
        }
    }
    
    /**
     * Lists the files in a directory
     * @todo flags to make it more like ls in linux
     */
    private class List extends Command
    {
        public List(String token, int argCount, String description)
        {
            super(token,argCount,description);
        }

        public void run(ArrayList<String> args)
        {
            if(currentDir != null)
            {
                try
                {
                    fileList = currentDir.listFiles(false, false); //for the download record

                    ArrayList<String> fileListUse = currentDir.listFiles(false, true); //for the usability

                    int i = 0;
                    for(String file : fileListUse)
                    {
                        System.out.println(i++ +" "+file);
                    }
                }
                catch(Exception e)
                {
                    System.out.println("Error: "+e.getMessage());
                }
            }
        }
    }
}
