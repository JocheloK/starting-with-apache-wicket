package app.web.pages.home;

import app.model.*;
import app.services.*;
import app.web.pages.*;
import com.giffing.wicket.spring.boot.context.scan.*;
import com.mongodb.client.gridfs.*;
import lombok.*;
import lombok.extern.slf4j.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.wicket.ajax.*;
import org.apache.wicket.ajax.form.*;
import org.apache.wicket.ajax.markup.html.*;
import org.apache.wicket.ajax.markup.html.form.*;
import org.apache.wicket.event.*;
import org.apache.wicket.markup.html.*;
import org.apache.wicket.markup.html.basic.*;
import org.apache.wicket.markup.html.form.*;
import org.apache.wicket.markup.html.form.upload.*;
import org.apache.wicket.markup.html.link.*;
import org.apache.wicket.markup.html.list.*;
import org.apache.wicket.markup.html.panel.*;
import org.apache.wicket.model.*;
import org.apache.wicket.spring.injection.annot.*;
import org.apache.wicket.util.lang.*;
import org.apache.wicket.util.resource.*;
import org.wicketstuff.annotation.mount.*;

import java.io.*;
import java.util.*;
import java.util.stream.*;

@WicketHomePage
@MountPath(value = "home", alt = {"home2"})
@Slf4j
public class HomePage extends BasePage {

  @SpringBean
  private ExcelGeneratorService excelGeneratorService;

  @SpringBean
  private PdfGeneratorService pdfGeneratorService;

  @SpringBean
  private MongoDBService mongoDBService;

  @SpringBean
  private AttachmentsService attachmentsService;

  AJAXDownload downloadExcel;
  private FileUploadField fileUploadField;

  FeedbackPanel fp;

  public HomePage() {

    Label alertLabel = new Label("alertLabel", PropertyModel.of(this, "msg"));

    WebMarkupContainer alertBox = new WebMarkupContainer("alertBox") {
      boolean v = false;

      @Override
      protected void onConfigure() {
        super.onConfigure();
        //System.out.println("AlertBox Configure ...");
      }

      @Override
      public void onEvent(IEvent<?> event) {
        super.onEvent(event);
        //System.out.println("Event received ............." + event.getPayload());
        if (event.getPayload() instanceof AlertMessageEvent) {
          AlertMessageEvent a = ((AlertMessageEvent) event.getPayload());
          a.getTarget().add(this);
        }
      }
    };

    alertBox.setOutputMarkupPlaceholderTag(true);
    alertBox.setVisible(false);
    alertBox.setOutputMarkupId(true);
    add(alertBox);

    fp = new FeedbackPanel("feedbackPanel");
    fp.setOutputMarkupPlaceholderTag(true);
    fp.setOutputMarkupId(true);
    add(fp);

    List<Todo> todos = mongoDBService.getAllItems();
    ListView<Todo> todosList = new ListView<>("todosList", todos) {
      @Override
      protected void populateItem(ListItem<Todo> item) {
        // se pueden agregar los campos porpropertyModel infiriendo o escribiendo el tipo
        // se puede usar directamente con una expresion lambda
        item.add(new CheckBox("selected", new PropertyModel<>(item.getModel(), "selected")));
        item.add(new Label("title", new PropertyModel<String>(item.getModel(), "title")));
        item.add(new Label("body", () -> item.getModelObject().getBody()));

      }
    };
    todosList.setOutputMarkupPlaceholderTag(true);
    todosList.setOutputMarkupId(true);
    todosList.setReuseItems(true);

    WebMarkupContainer sectionForm = new WebMarkupContainer("sectionForm");
    sectionForm.setOutputMarkupPlaceholderTag(true);
    sectionForm.setVisible(true);
    Form<Void> form = new Form<>("todo");

    WebMarkupContainer formNew = new WebMarkupContainer("formNew");
    formNew.setOutputMarkupPlaceholderTag(true);
    formNew.setVisible(false);

    AjaxLink<Void> btn1 = new AjaxLink<>("addItemLink") {
      @Override
      public void onClick(AjaxRequestTarget target) {
        formNew.setVisible(!formNew.isVisible());

        target.add(formNew);
      }
    };
    form.add(btn1);

    AJAXDownload downloadPdf = new AJAXDownload() {
      @Override
      protected String getFileName() {
        return "todos.pdf";
      }

      @Override
      protected IResourceStream getResourceStream() {
        return new AbstractResourceStreamWriter() {
          @Override
          public void write(OutputStream output) throws IOException {
            output.write(pdfGeneratorService.createdPdf(todos).toByteArray());
          }
        };
      }
    };

    AjaxLink<Void> downloadPdfBtn = new AjaxLink<>("downloadPdfBtn") {
      @Override
      public void onClick(AjaxRequestTarget target) {
        downloadPdf.initiate(target);
      }
    };
    form.add(downloadPdfBtn);
    form.add(downloadPdf);

    downloadExcel = new AJAXDownload() {
      @Override
      protected String getFileName() {
        return "excel-todos.xlsx";
      }

      @Override
      protected IResourceStream getResourceStream() {
        return new AbstractResourceStreamWriter() {
          @Override
          public void write(OutputStream output) throws IOException {
            Workbook wb = excelGeneratorService.createExcelFile(todos);
            wb.write(output);
          }
        };
      }
    };

    AjaxLink<Void> downloadExcelBtn = new AjaxLink<>("downloadExcelBtn") {
      @Override
      public void onClick(AjaxRequestTarget target) {
        HomePage.this.downloadExcel.initiate(target);
      }
    };
    form.add(downloadExcelBtn);
    form.add(this.downloadExcel);

    add(sectionForm);

    Todo todoItem = new Todo();

    form.setOutputMarkupId(true);
    form.setDefaultModel(new CompoundPropertyModel<>(todoItem));
    sectionForm.add(form);

    TextField<String> title = new TextField<>("title");
    TextField<String> body = new TextField<>("body");
    formNew.add(title);
    formNew.add(body);

    // we can use AjaxButton instead of AjaxLink
    AjaxButton btnSave = new AjaxButton("save") {
      @Override
      protected void onSubmit(AjaxRequestTarget target) {

        /* add content validation */
        if (title.getValue().isEmpty()) {
          showMessage(target, "Title is required", MessageType.Warn.toString());
          return;
        }
        if (body.getValue().isEmpty()) {
          showMessage(target, "Body is required", MessageType.Warn.toString());
          return;
        }

        Todo todo = new Todo();
        todo.setTitle(title.getValue());
        todo.setBody(todoItem.getBody());
        mongoDBService.addToItems(todo);
// add a method to refresh
        refreshTodosList(todos);

        formNew.setVisible(false);

        todoItem.setTitle("");
        todoItem.setBody("");
        target.add(sectionForm);
        //change the message method
        showMessage(target, "Todo saved into database", MessageType.Info.toString());
      }

      @Override
      protected void onError(AjaxRequestTarget target) {
        showMessage(target, "Error to save Todo", MessageType.Error.toString());
      }
    };

    formNew.add(btnSave);


    AjaxLink<Void> btnRemove = new AjaxLink<Void>("remove") {
      @Override
      public void onClick(AjaxRequestTarget target) {
        List<Todo> todosToRemove = todos.stream().filter(todo -> todo.isSelected()).collect(Collectors.toList());
        mongoDBService.removeItems(todosToRemove);

        refreshTodosList(todos);

        showMessage(target, "Selected items removed ...", MessageType.Info.toString());
        target.add(sectionForm);
      }
    };
    btnRemove.add(new AjaxFormSubmitBehavior(form, "click") {
    });
    form.add(btnRemove);

    form.add(formNew, todosList);


    AjaxLink<Void> btnSelectAll = new AjaxLink<Void>("btnSelectAll") {
      @Override
      public void onClick(AjaxRequestTarget target) {
        todos.forEach(todo -> todo.setSelected(true));
        target.add(sectionForm);
      }
    };

    AjaxLink<Void> btnDeSelectAll = new AjaxLink<Void>("btnDeSelectAll") {
      @Override
      public void onClick(AjaxRequestTarget target) {
        todos.forEach(todo -> todo.setSelected(false));
        target.add(sectionForm);
      }
    };
    add(btnSelectAll, btnDeSelectAll);


    WebMarkupContainer filesListSection = new WebMarkupContainer("filesListSection");
    filesListSection.setOutputMarkupId(true);
    filesListSection.setOutputMarkupPlaceholderTag(true);
    add(filesListSection);

    List<FileItem> files = new ArrayList<>();
    updateFilesList(files);

    Form<Void> formUpload = new Form<>("formUpload") {
      @Override
      protected void onSubmit() {
        super.onSubmit();
        FileUpload fileUpload = fileUploadField.getFileUpload();

        try {
          attachmentsService.storeInGridFS(fileUpload.getInputStream(), fileUpload.getClientFileName(), fileUpload.getSize());
          info("Upload completed for file: " + fileUpload.getClientFileName());

          updateFilesList(files);
        } catch (Exception e) {
          e.printStackTrace();
          error("Upload failed!");
        }
      }
    };
    fileUploadField = new FileUploadField("fileUploadField");
    formUpload.add(fileUploadField);

    formUpload.setMultiPart(true);
    formUpload.setMaxSize(Bytes.kilobytes(100));
    filesListSection.add(formUpload);

    ListView<FileItem> filesList = new ListView<>("filesList", files) {
      @Override
      protected void populateItem(ListItem<FileItem> item) {
        item.add(new ExternalLink("externalLink", () -> "/files/download?id=" + item.getModelObject().getId(), Model.of(item.getModelObject().getFilename())));
        item.add(new Label("length", new PropertyModel<String>(item.getModel(), "length")));

        AjaxLink<Void> removeLink = new AjaxLink<>("removeLink") {
          @Override
          public void onClick(AjaxRequestTarget target) {
            showMessage(target, "File removed: " + item.getModelObject().getFilename(), MessageType.Info.toString());
            attachmentsService.deleteAttachment(item.getModelObject().getId());
            updateFilesList(files);
            target.add(filesListSection);
          }
        };
        item.add(removeLink);
      }
    };
    filesList.setOutputMarkupPlaceholderTag(true);
    filesList.setOutputMarkupId(true);
    filesList.setReuseItems(true);
    filesListSection.add(filesList);
  }

  private void refreshTodosList(List<Todo> todos) {
    todos.clear();
    todos.addAll(mongoDBService.getAllItems());
  }

  private void updateFilesList(List<FileItem> files) {
    GridFSFindIterable filesIterable = attachmentsService.listAllFiles();
    files.clear();
    files.addAll(StreamSupport.stream(filesIterable.spliterator(), false).map(gridFSFile -> new FileItem(gridFSFile.getObjectId().toString(), gridFSFile.getFilename(), gridFSFile.getLength())).collect(Collectors.toList()));
  }

  protected void showMessage(AjaxRequestTarget target, String msg, String typeMessage) {
    if (MessageType.Info.toString().equals(typeMessage)) {
      info(msg);
    }
    if (MessageType.Warn.toString().equals(typeMessage)) {
      warn(msg);
    }
    if (MessageType.Error.toString().equals(typeMessage)) {
      error(msg);
    }
    target.add(fp);
  }

  @Data
  @AllArgsConstructor
  private class FileItem implements Serializable {
    String id;
    String filename;
    long length;
  }
  public enum MessageType {
    Info, Warn, Error
  }

}
